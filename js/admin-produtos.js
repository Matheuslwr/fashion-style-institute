(() => {
  const apiBase = (window.FSI_API_BASE_URL || '').replace(/\/$/, '');
  const login = document.querySelector('[data-admin-login]');
  const workspace = document.querySelector('[data-admin-workspace]');
  const loginForm = document.querySelector('[data-login-form]');
  const loginFeedback = document.querySelector('[data-login-feedback]');
  const adminFeedback = document.querySelector('[data-admin-feedback]');
  const productForm = document.querySelector('[data-product-form]');
  const productEditor = document.querySelector('[data-product-editor]');
  const productList = document.querySelector('[data-admin-product-list]');
  const categorySelect = document.querySelector('[data-category-select]');
  const categoryCreator = document.querySelector('[data-new-category]');
  const categoryNameInput = document.querySelector('[data-new-category-name]');
  const imageList = document.querySelector('[data-image-list]');
  const variantList = document.querySelector('[data-variant-list]');
  let authorization = '';
  let categories = [];
  let products = [];
  let images = [];
  let uploadInProgress = false;

  const request = async (path, options = {}) => {
    let response;
    try {
      response = await fetch(`${apiBase}${path}`, {
        ...options,
        headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
          ...(authorization ? { Authorization: authorization } : {}), ...options.headers }
      });
    } catch {
      throw new Error('Não foi possível conectar à API. Confira se o backend está em execução.');
    }
    if (response.status === 401 || response.status === 403) {
      authorization = '';
      workspace.hidden = true;
      login.hidden = false;
      throw new Error('Acesso negado. Confira usuário e senha.');
    }
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.message || `Falha na API (${response.status}).`);
    }
    return response.status === 204 ? null : response.json();
  };

  function createVariantRow(variant = {}) {
    const row = document.createElement('div');
    row.className = 'admin-variant-row';
    [['color', 'Cor', variant.color || ''], ['size', 'Tamanho', variant.size || ''],
      ['sku', 'SKU', variant.sku || ''], ['stock', 'Estoque', variant.stock ?? 0]].forEach(([name, label, value]) => {
      const input = document.createElement('input');
      input.name = `variant-${name}`;
      input.placeholder = label;
      input.setAttribute('aria-label', label);
      input.value = String(value);
      input.required = true;
      if (name === 'stock') { input.type = 'number'; input.min = '0'; input.step = '1'; }
      row.append(input);
    });
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = '×';
    remove.setAttribute('aria-label', 'Remover variante');
    remove.addEventListener('click', () => row.remove());
    row.append(remove);
    variantList.append(row);
  }

  function renderImages() {
    imageList.replaceChildren(...images.map((image, index) => {
      const item = document.createElement('div');
      item.className = 'admin-image-item';
      const preview = document.createElement('img');
      preview.src = resolveImage(image.imageUrl);
      preview.alt = image.altText;
      const label = document.createElement('span');
      label.textContent = image.color || image.altText;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = '×';
      remove.setAttribute('aria-label', `Remover imagem ${image.altText}`);
      remove.addEventListener('click', () => { images.splice(index, 1); renderImages(); });
      item.append(preview, label, remove);
      return item;
    }));
  }

  function resolveImage(path) {
    if (/^https?:\/\//i.test(path)) return path;
    if (path.startsWith('/uploads/')) return `${apiBase}${path}`;
    const script = document.querySelector('script[src*="admin-produtos.js"]');
    const siteRoot = new URL('../', script.src);
    return new URL(path.replace(/^\//, ''), siteRoot).href;
  }

  function renderCategories(selectedId = '') {
    categorySelect.replaceChildren(...categories.map(category => {
      const option = document.createElement('option');
      option.value = category.id;
      option.textContent = category.name;
      option.selected = category.id === selectedId;
      return option;
    }));
  }

  function renderProducts() {
    if (!products.length) {
      const empty = document.createElement('p');
      empty.className = 'admin-hint';
      empty.textContent = 'Nenhum produto cadastrado.';
      productList.replaceChildren(empty);
      return;
    }
    productList.replaceChildren(...products.map(product => {
      const row = document.createElement('article');
      row.className = 'admin-product-row';
      const info = document.createElement('div');
      const name = document.createElement('strong');
      name.textContent = product.name;
      const status = document.createElement('small');
      status.textContent = `${product.category.name} · ${product.active ? 'Ativo' : 'Inativo'}${product.featured ? ' · Destaque' : ''}`;
      info.append(name, status);
      const actions = document.createElement('div');
      actions.className = 'admin-product-actions';
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.textContent = 'Editar';
      edit.addEventListener('click', () => editProduct(product));
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = 'Excluir';
      remove.addEventListener('click', () => deleteProduct(product));
      actions.append(edit, remove);
      row.append(info, actions);
      return row;
    }));
  }

  async function loadData() {
    [categories, products] = await Promise.all([
      request('/api/categories'),
      request('/api/admin/products')
    ]);
    renderCategories();
    renderProducts();
  }

  function showEditor(product = null) {
    productForm.reset();
    productForm.elements.databaseId.value = product?.databaseId || '';
    productForm.elements.name.value = product?.name || '';
    productForm.elements.description.value = product?.description || '';
    productForm.elements.price.value = product ? (product.priceCents / 100).toFixed(2) : '';
    productForm.elements.promotionalPrice.value = product?.promotionalPriceCents == null
      ? '' : (product.promotionalPriceCents / 100).toFixed(2);
    productForm.elements.featured.checked = product?.featured || false;
    productForm.elements.active.checked = product?.active ?? true;
    renderCategories(product?.category.id || categories[0]?.id || '');
    images = product ? product.images.map(image => ({ ...image })) : [];
    renderImages();
    variantList.replaceChildren();
    (product?.variants.length ? product.variants : [{}]).forEach(createVariantRow);
    document.querySelector('[data-editor-title]').textContent = product ? 'Editar produto' : 'Novo produto';
    productEditor.hidden = false;
    productEditor.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function editProduct(product) { showEditor(product); }

  async function deleteProduct(product) {
    if (!window.confirm(`Excluir "${product.name}"? Esta ação não pode ser desfeita.`)) return;
    try {
      await request(`/api/admin/products/${product.databaseId}`, { method: 'DELETE' });
      await loadData();
      adminFeedback.textContent = 'Produto excluído.';
    } catch (error) { adminFeedback.textContent = error.message; }
  }

  async function uploadImages(files) {
    const name = productForm.elements.name.value.trim() || 'Produto';
    const color = productForm.elements.imageColor.value.trim();
    for (const file of files) {
      const body = new FormData();
      body.append('file', file);
      const result = await request('/api/admin/images', { method: 'POST', body });
      images.push({ imageUrl: result.imageUrl, altText: `${name}${color ? ` na cor ${color}` : ''}`, color, displayOrder: images.length });
    }
    renderImages();
  }

  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    const username = loginForm.elements.username.value.trim();
    const password = loginForm.elements.password.value;
    authorization = `Basic ${btoa(`${username}:${password}`)}`;
    loginFeedback.textContent = 'Verificando acesso...';
    try {
      await loadData();
      login.hidden = true;
      workspace.hidden = false;
      loginForm.reset();
      loginFeedback.textContent = '';
    } catch (error) {
      authorization = '';
      loginFeedback.textContent = error.message;
    }
  });

  document.querySelector('[data-new-product]').addEventListener('click', () => showEditor());
  document.querySelectorAll('[data-cancel-edit]').forEach(button => button.addEventListener('click', () => { productEditor.hidden = true; }));
  document.querySelector('[data-add-variant]').addEventListener('click', () => createVariantRow());
  document.querySelector('[data-add-category]').addEventListener('click', () => {
    categoryCreator.hidden = false;
    categoryNameInput.focus();
  });
  document.querySelector('[data-cancel-new-category]').addEventListener('click', () => {
    categoryCreator.hidden = true;
    categoryNameInput.value = '';
  });
  document.querySelector('[data-create-category]').addEventListener('click', async () => {
    const name = categoryNameInput.value.trim();
    const feedback = document.querySelector('[data-form-feedback]');
    if (!name) {
      feedback.textContent = 'Digite o nome da categoria.';
      categoryNameInput.focus();
      return;
    }
    try {
      const category = await request('/api/admin/categories', { method: 'POST', body: JSON.stringify({ name }) });
      categories.push(category);
      categories.sort((left, right) => left.name.localeCompare(right.name, 'pt-BR'));
      renderCategories(category.id);
      categoryNameInput.value = '';
      categoryCreator.hidden = true;
      feedback.textContent = 'Categoria criada.';
    } catch (error) { feedback.textContent = error.message; }
  });
  document.querySelector('[data-logout]').addEventListener('click', () => {
    authorization = '';
    products = [];
    workspace.hidden = true;
    login.hidden = false;
    productEditor.hidden = true;
  });
  document.querySelector('[data-image-files]').addEventListener('change', async event => {
    const fileInput = event.target;
    const files = [...fileInput.files];
    const feedback = document.querySelector('[data-form-feedback]');
    const saveButton = document.querySelector('[data-save-product]');
    fileInput.value = '';
    if (!files.length) return;
    uploadInProgress = true;
    saveButton.disabled = true;
    feedback.textContent = 'Enviando imagem...';
    try {
      await uploadImages(files);
      feedback.textContent = `${files.length} imagem(ns) enviada(s). Confira a miniatura antes de salvar.`;
    } catch (error) { feedback.textContent = error.message; }
    finally {
      uploadInProgress = false;
      saveButton.disabled = false;
    }
  });

  productForm.addEventListener('submit', async event => {
    event.preventDefault();
    const feedback = document.querySelector('[data-form-feedback]');
    if (uploadInProgress) {
      feedback.textContent = 'Aguarde o término do envio da imagem antes de salvar.';
      return;
    }
    feedback.textContent = 'Salvando produto...';
    const variants = [...variantList.querySelectorAll('.admin-variant-row')].map(row => ({
      color: row.querySelector('[name="variant-color"]').value.trim(),
      size: row.querySelector('[name="variant-size"]').value.trim(),
      sku: row.querySelector('[name="variant-sku"]').value.trim(),
      stock: Number(row.querySelector('[name="variant-stock"]').value)
    }));
    const priceCents = Math.round(Number(productForm.elements.price.value) * 100);
    const promotionalValue = productForm.elements.promotionalPrice.value;
    if (!images.length) {
      feedback.textContent = 'Adicione pelo menos uma imagem ao produto.';
      return;
    }
    if (promotionalValue !== '' && Math.round(Number(promotionalValue) * 100) >= priceCents) {
      feedback.textContent = 'O preço promocional deve ser menor que o preço normal.';
      return;
    }
    const payload = {
      name: productForm.elements.name.value.trim(),
      description: productForm.elements.description.value.trim(),
      priceCents,
      promotionalPriceCents: promotionalValue === '' ? null : Math.round(Number(promotionalValue) * 100),
      categoryId: productForm.elements.categoryId.value,
      featured: productForm.elements.featured.checked,
      active: productForm.elements.active.checked,
      images,
      variants
    };
    try {
      const id = productForm.elements.databaseId.value;
      await request(id ? `/api/admin/products/${id}` : '/api/admin/products', {
        method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)
      });
      await loadData();
      productEditor.hidden = true;
      adminFeedback.textContent = 'Produto salvo. Ele já está disponível na loja conforme categoria e status.';
    } catch (error) { feedback.textContent = error.message; }
  });
})();