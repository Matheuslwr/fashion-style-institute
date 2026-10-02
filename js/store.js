(() => {
  const products = [
    { id: 'vestido-liliany', name: 'Vestido LILIANY', price: 22990, note: 'Tule · modelagem elegante', missingSizesByColor: { 'Azul-marinho': ['39'], 'Azul serenity': ['41'], Laranja: ['40', '41'], Rosé: [], Roxo: ['39', '42'], Verde: ['42'], Vermelho: ['40'] }, unavailableColors: ['Rosé'], images: { 'Azul-marinho': 'Vestido1-Azul-Marinho.webp', 'Azul serenity': 'Vestido1-Azul-Serenity.webp', Laranja: 'Vestido1-Laranja.webp', Rosé: 'Vestido1-Rosé.webp', Roxo: 'Vestido1-Roxo.webp', Verde: 'Vestido1-Verde.webp', Vermelho: 'Vestido1-Vermelho.webp' } },
    { id: 'vestido-amelia', name: 'Vestido AMÉLIA', price: 24990, note: 'Caimento fluido · cores intensas', missingSizesByColor: { 'Azul-marinho': ['41'], Marsala: [], 'Verde oliva': ['39', '42'] }, unavailableColors: ['Marsala'], images: { 'Azul-marinho': 'VestidoAmeliaAzulMarinho.webp', Marsala: 'VestidoAmeliaMarsala.webp', 'Verde oliva': 'VestidoAmeliaVerdeOliva.png' } },
    { id: 'vestido-laura', name: 'Vestido LAURA', price: 21990, note: 'Silhueta marcante · toque leve', missingSizesByColor: { 'Azul serenity': ['40'], Fúcsia: ['39', '41'], Marsala: ['42'], 'Verde oliva': ['38'] }, unavailableColors: [], images: { 'Azul serenity': 'VestidoLauraAzulSerenity.webp', Fúcsia: 'VestidoLauraFúcsia.webp', Marsala: 'VestidoLauraMarsala.webp', 'Verde oliva': 'VestidoLauraVerdeOliva.webp' } },
    { id: 'vestido-lira', name: 'Vestido LIRA', price: 23990, note: 'Design contemporâneo · várias cores', missingSizesByColor: { 'Azul bic': ['39', '42'], Fúcsia: ['41'], Marrom: ['40'], Uva: [], Vinho: ['39', '41'] }, unavailableColors: ['Uva'], images: { 'Azul bic': 'VestidoLiraAzulBic.webp', Fúcsia: 'VestidoLiraFúcsia.webp', Marrom: 'VestidoLiraMarrom.webp', Uva: 'VestidoLiraUva.webp', Vinho: 'VestidoLiraVinho.webp' } },
    { id: 'vestido-nanda', name: 'Vestido NANDA', price: 19990, note: 'Conforto e cor para o dia a dia', missingSizesByColor: { 'Azul-marinho': ['40', '42'], 'Azul serenity': ['38', '41'] }, unavailableColors: [], images: { 'Azul-marinho': 'VestidoNandaAzulMarinho.webp', 'Azul serenity': 'VestidoNandaAzulSerenity.webp' } },
    { id: 'vestido-zaza', name: 'Vestido ZAZA', price: 18990, note: 'Leveza · paleta vibrante', missingSizesByColor: { Amarelo: ['39', '41'], Fúcsia: ['40'], 'Verde menta': ['38', '42'], 'Verde oliva': ['39', '40'] }, unavailableColors: [], images: { Amarelo: 'VestidoZazaAmarelo.webp', Fúcsia: 'VestidoZazaFúcsia.webp', 'Verde menta': 'VestidoZazaVerdeMenta.webp', 'Verde oliva': 'VestidoZazaVerdeOliva.webp' } }
  ];
  const productsById = new Map(products.map((item) => [item.id, item]));
  const product = productsById.get(new URLSearchParams(window.location.search).get('produto')) || products[0];
  const cartKey = 'fsi-demo-cart-v1';
  const orderKey = 'fsi-demo-order-v1';
  const availableSizes = ['38', '39', '40', '41', '42'];
  const shippingOrigin = { latitude: -25.4284, longitude: -49.2733 };
  const brazilStates = [
    ['AC', 'Acre'], ['AL', 'Alagoas'], ['AP', 'Amapá'], ['AM', 'Amazonas'], ['BA', 'Bahia'],
    ['CE', 'Ceará'], ['DF', 'Distrito Federal'], ['ES', 'Espírito Santo'], ['GO', 'Goiás'],
    ['MA', 'Maranhão'], ['MT', 'Mato Grosso'], ['MS', 'Mato Grosso do Sul'],
    ['MG', 'Minas Gerais'], ['PA', 'Pará'], ['PB', 'Paraíba'], ['PR', 'Paraná'],
    ['PE', 'Pernambuco'], ['PI', 'Piauí'], ['RJ', 'Rio de Janeiro'],
    ['RN', 'Rio Grande do Norte'], ['RS', 'Rio Grande do Sul'], ['RO', 'Rondônia'],
    ['RR', 'Roraima'], ['SC', 'Santa Catarina'], ['SP', 'São Paulo'], ['SE', 'Sergipe'],
    ['TO', 'Tocantins']
  ].map(([uf, name]) => ({ uf, name }));
  const citiesByState = new Map();
  let shippingQuote = null;
  let shippingRequestId = 0;
  let cityRequestId = 0;
  let toastTimeout = null;
  let cartDrawerReturnFocus = null;
  const formatMoney = (cents) => new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(cents / 100);

  function productSizes(item, color) {
    return availableSizes.filter((size) => !item.missingSizesByColor[color].includes(size));
  }

  function imagePath(item, color) {
    return `../img/Produtos/${item.images[color]}`;
  }

  function initializeStoreSearch() {
    const header = document.querySelector('.store-header');
    const actions = header?.querySelector('.store-actions');
    if (!header || !actions) return;

    let search = header.querySelector('[data-store-search]');
    let toggle = header.querySelector('[data-search-toggle]');
    if (!toggle) {
      toggle = document.createElement('button');
      toggle.className = 'icon-button';
      toggle.type = 'button';
      toggle.dataset.searchToggle = '';
      toggle.setAttribute('aria-label', 'Buscar produtos');
      toggle.setAttribute('aria-expanded', 'false');
      toggle.setAttribute('aria-controls', 'store-search-panel');
      toggle.textContent = '⌕';
      actions.insertBefore(toggle, actions.firstChild);
    }
    if (!search) {
      search = document.createElement('div');
      search.className = 'store-search';
      search.id = 'store-search-panel';
      search.dataset.storeSearch = '';
      search.setAttribute('role', 'search');
      search.hidden = true;
      const inputRow = document.createElement('div');
      inputRow.className = 'store-search-input-row';
      const label = document.createElement('label');
      label.className = 'sr-only';
      label.htmlFor = 'store-search-input';
      label.textContent = 'Buscar produtos';
      const input = document.createElement('input');
      input.id = 'store-search-input';
      input.type = 'search';
      input.autocomplete = 'off';
      input.placeholder = 'Nome, cor, tamanho, preço...';
      input.dataset.searchInput = '';
      const closeButton = document.createElement('button');
      closeButton.className = 'store-search-close';
      closeButton.type = 'button';
      closeButton.dataset.searchClose = '';
      closeButton.setAttribute('aria-label', 'Fechar busca');
      closeButton.textContent = '×';
      inputRow.append(label, input, closeButton);
      const results = document.createElement('div');
      results.className = 'store-search-results';
      results.dataset.searchResults = '';
      results.setAttribute('aria-live', 'polite');
      const hint = document.createElement('p');
      hint.className = 'store-search-hint';
      hint.textContent = 'Digite para buscar nos produtos.';
      results.append(hint);
      search.append(inputRow, results);
      header.append(search);
    }
    const input = search?.querySelector('[data-search-input]');
    const results = search?.querySelector('[data-search-results]');
    if (!search || !toggle || !input || !results) return;

    const searchText = (item) => {
      const colors = Object.keys(item.images);
      const sizes = colors.flatMap((color) => productSizes(item, color));
      const price = (item.price / 100).toFixed(2);
      return normalizeLocation([
        item.name,
        item.note,
        ...colors,
        ...sizes,
        formatMoney(item.price),
        price,
        price.replace('.', ','),
        Math.floor(item.price / 100),
        item.price,
        'vestido roupa moda'
      ].join(' ')).replace(/[^a-z0-9]+/g, ' ');
    };

    const renderResults = (query) => {
      const terms = normalizeLocation(query).replace(/[^a-z0-9]+/g, ' ').trim().split(/\s+/).filter(Boolean);
      if (!terms.length) {
        const hint = document.createElement('p');
        hint.className = 'store-search-hint';
        hint.textContent = 'Digite nome, cor, tamanho ou preço para buscar.';
        results.replaceChildren(hint);
        return;
      }
      const matches = products.filter((item) => {
        const content = searchText(item);
        return terms.every((term) => content.includes(term));
      });
      if (!matches.length) {
        const empty = document.createElement('p');
        empty.className = 'store-search-empty';
        empty.textContent = 'Nenhum produto encontrado. Tente outro nome, cor ou tamanho.';
        results.replaceChildren(empty);
        return;
      }
      results.replaceChildren(...matches.map((item) => {
        const result = document.createElement('a');
        result.className = 'store-search-result';
        result.href = `produto-camisa-essencia.html?produto=${encodeURIComponent(item.id)}`;
        const image = document.createElement('img');
        const firstAvailableColor = Object.keys(item.images).find((color) => !item.unavailableColors.includes(color));
        image.src = imagePath(item, firstAvailableColor);
        image.alt = '';
        const copy = document.createElement('span');
        copy.className = 'store-search-result-copy';
        const name = document.createElement('strong');
        name.textContent = item.name;
        const note = document.createElement('small');
        note.textContent = `${item.note} · ${Object.keys(item.images).length} cores`;
        copy.append(name, note);
        const price = document.createElement('span');
        price.className = 'store-search-result-price';
        price.textContent = formatMoney(item.price);
        result.append(image, copy, price);
        return result;
      }));
    };

    const closeSearch = () => {
      search.hidden = true;
      toggle.setAttribute('aria-expanded', 'false');
      toggle.focus();
    };

    toggle.addEventListener('click', () => {
      const willOpen = search.hidden;
      search.hidden = !willOpen;
      toggle.setAttribute('aria-expanded', String(willOpen));
      if (willOpen) {
        renderResults(input.value);
        input.focus();
      } else {
        toggle.focus();
      }
    });
    input.addEventListener('input', () => renderResults(input.value));
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') closeSearch();
      if (event.key === 'Enter') {
        const firstResult = results.querySelector('.store-search-result');
        if (firstResult) window.location.href = firstResult.href;
      }
    });
    search.querySelector('[data-search-close]').addEventListener('click', closeSearch);
  }

  function createCatalogCard(item, headingTag = 'h2') {
    const card = document.createElement('a');
    card.className = 'product-card product-card-link';
    card.href = `produto-camisa-essencia.html?produto=${encodeURIComponent(item.id)}`;
    const visual = document.createElement('div');
    visual.className = 'product-image product-image-photo';
    const color = Object.keys(item.images).find((name) => !item.unavailableColors.includes(name));
    const image = document.createElement('img');
    image.src = imagePath(item, color);
    image.alt = `${item.name}, ${color}`;
    image.loading = 'lazy';
    visual.append(image);
    const type = document.createElement('p');
    type.className = 'product-type';
    type.textContent = `Vestir · ${Object.keys(item.images).length} cores`;
    const name = document.createElement(headingTag);
    name.textContent = item.name;
    const note = document.createElement('p');
    note.className = 'product-note';
    note.textContent = item.note;
    const price = document.createElement('strong');
    price.className = 'product-price';
    price.textContent = formatMoney(item.price);
    card.append(visual, type, name, note, price);
    return card;
  }

  function initializeRecommendations() {
    const section = document.querySelector('[data-product-recommendations]');
    const list = section?.querySelector('[data-recommendation-list]');
    if (!section || !list) return;
    const recommendations = products.filter((item) => item.id !== product.id);
    for (let index = recommendations.length - 1; index > 0; index -= 1) {
      const randomIndex = Math.floor(Math.random() * (index + 1));
      [recommendations[index], recommendations[randomIndex]] = [recommendations[randomIndex], recommendations[index]];
    }
    list.replaceChildren(...recommendations.slice(0, 3).map((item) => createCatalogCard(item, 'h3')));
    section.hidden = recommendations.length === 0;
  }

  function readCart() {
    try {
      const savedCart = JSON.parse(localStorage.getItem(cartKey) || '[]');
      if (!Array.isArray(savedCart)) return [];
      return savedCart.filter((item) => {
        const cartProduct = productsById.get(item.productId);
        return cartProduct
          && Object.hasOwn(cartProduct.images, item.color)
          && !cartProduct.unavailableColors.includes(item.color)
          && productSizes(cartProduct, item.color).includes(String(item.size))
          && Number.isInteger(item.quantity)
          && item.quantity > 0;
      }).map((item) => ({
        productId: item.productId,
        color: item.color,
        size: String(item.size),
        quantity: Math.min(item.quantity, 99)
      }));
    } catch {
      return [];
    }
  }

  function normalizeLocation(value) {
    return value.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');
  }

  function findState(value) {
    const normalizedValue = normalizeLocation(value);
    return brazilStates.find((state) => state.uf.toLocaleLowerCase('pt-BR') === normalizedValue
      || normalizeLocation(state.name) === normalizedValue);
  }

  function fillDatalist(list, values) {
    if (!list) return;
    list.replaceChildren(...values.map((value) => {
      const option = document.createElement('option');
      option.value = value;
      return option;
    }));
  }

  async function loadCitiesForState(uf, selectedCity = '') {
    const cityInput = document.querySelector('[name="city"]');
    const cityList = document.querySelector('[data-city-options]');
    const cityFeedback = document.querySelector('[data-city-feedback]');
    if (!cityInput || !cityList) return;
    const requestId = ++cityRequestId;
    cityInput.value = '';
    cityInput.disabled = true;
    fillDatalist(cityList, []);
    if (cityFeedback) cityFeedback.textContent = 'Carregando cidades do estado selecionado...';

    try {
      let cities = citiesByState.get(uf);
      if (!cities) {
        const response = await fetch(`https://servicodados.ibge.gov.br/api/v1/localidades/estados/${uf}/municipios?orderBy=nome`);
        if (!response.ok) throw new Error('Não foi possível carregar as cidades');
        cities = (await response.json()).map((city) => city.nome);
        citiesByState.set(uf, cities);
      }
      if (requestId !== cityRequestId || findState(document.querySelector('[name="region"]')?.value || '')?.uf !== uf) return;
      fillDatalist(cityList, cities);
      cityInput.disabled = false;
      const matchingCity = cities.find((city) => normalizeLocation(city) === normalizeLocation(selectedCity));
      if (matchingCity) cityInput.value = matchingCity;
      if (cityFeedback) cityFeedback.textContent = `Digite para filtrar as ${cities.length} cidades de ${findState(uf).name}.`;
    } catch {
      if (requestId !== cityRequestId) return;
      cityInput.disabled = false;
      if (cityFeedback) cityFeedback.textContent = 'Não foi possível carregar a lista agora. Confira sua conexão e tente novamente.';
    }
  }

  function initializeAddressSuggestions() {
    const stateInput = document.querySelector('[name="region"]');
    const cityInput = document.querySelector('[name="city"]');
    const stateList = document.querySelector('[data-state-options]');
    if (!stateInput || !cityInput || !stateList) return;
    fillDatalist(stateList, brazilStates.map((state) => state.name));
    stateInput.addEventListener('input', () => {
      const state = findState(stateInput.value);
      const stateFeedback = document.querySelector('[data-state-feedback]');
      const cityFeedback = document.querySelector('[data-city-feedback]');
      shippingQuote = null;
      renderShipping();
      if (state) {
        if (stateFeedback) stateFeedback.textContent = `Estado selecionado: ${state.name}.`;
        loadCitiesForState(state.uf);
      } else {
        cityRequestId += 1;
        cityInput.value = '';
        cityInput.disabled = true;
        fillDatalist(document.querySelector('[data-city-options]'), []);
        if (stateFeedback) {
          stateFeedback.textContent = stateInput.value
            ? 'Selecione um estado da lista.'
            : 'Digite e selecione um estado.';
        }
        if (cityFeedback) cityFeedback.textContent = 'Selecione um estado para ver as cidades disponíveis.';
      }
    });
    cityInput.addEventListener('input', () => {
      shippingQuote = null;
      renderShipping();
    });
  }

  function writeCart(cart) {
    try {
      localStorage.setItem(cartKey, JSON.stringify(cart));
    } catch {
      showFeedback('Não foi possível salvar o carrinho neste navegador.');
    }
    updateBagCount(cart);
    renderCart();
    renderCheckout();
  }

  function updateBagCount(cart = readCart()) {
    const count = cart.reduce((total, item) => total + item.quantity, 0);
    document.querySelectorAll('.bag-count').forEach((counter) => {
      counter.textContent = String(count);
      const link = counter.closest('a');
      if (link) link.setAttribute('aria-label', `Carrinho com ${count} ${count === 1 ? 'item' : 'itens'}`);
    });
  }

  function showFeedback(message) {
    const feedback = document.querySelector('[data-store-feedback]');
    if (feedback) feedback.textContent = message;
  }

  function showCartToast(message) {
    const toast = document.querySelector('[data-cart-toast]');
    if (!toast) return;
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => { toast.hidden = true; }, 5000);
  }

  function renderMiniCart(cart = readCart()) {
    const list = document.querySelector('[data-mini-cart-items]');
    if (!list) return;
    if (!cart.length) {
      const emptyMessage = document.createElement('p');
      emptyMessage.className = 'cart-drawer-empty';
      emptyMessage.textContent = 'Seu carrinho está vazio.';
      list.replaceChildren(emptyMessage);
    } else {
      list.replaceChildren(...cart.map((item) => {
        const cartProduct = productsById.get(item.productId);
        const row = document.createElement('article');
        row.className = 'cart-drawer-item';
        const image = document.createElement('img');
        image.src = imagePath(cartProduct, item.color);
        image.alt = `${cartProduct.name}, cor ${item.color}`;
        const details = document.createElement('div');
        details.className = 'cart-drawer-item-details';
        const name = document.createElement('h3');
        name.textContent = cartProduct.name;
        const variant = document.createElement('p');
        variant.textContent = `${item.color} · tamanho ${item.size}`;
        const price = document.createElement('strong');
        price.textContent = formatMoney(cartProduct.price);
        details.append(name, variant, price);
        const remove = document.createElement('button');
        remove.className = 'cart-drawer-remove';
        remove.type = 'button';
        remove.dataset.miniCartRemove = '';
        remove.dataset.cartKey = `${item.productId}|${item.color}|${item.size}`;
        remove.textContent = 'Remover';
        remove.setAttribute('aria-label', `Remover ${cartProduct.name}, ${item.color}, tamanho ${item.size} do carrinho`);
        row.append(image, details, remove);
        return row;
      }));
    }
    const subtotal = document.querySelector('[data-mini-cart-subtotal]');
    if (subtotal) subtotal.textContent = formatMoney(cartTotal(cart));
  }

  function openCartDrawer() {
    const drawer = document.querySelector('[data-cart-drawer]');
    if (!drawer) return;
    cartDrawerReturnFocus = document.activeElement;
    drawer.hidden = false;
    document.body.classList.add('cart-drawer-open');
    drawer.querySelector('[data-close-cart]')?.focus();
  }

  function closeCartDrawer() {
    const drawer = document.querySelector('[data-cart-drawer]');
    if (!drawer) return;
    drawer.hidden = true;
    document.body.classList.remove('cart-drawer-open');
    cartDrawerReturnFocus?.focus();
  }

  function makeCartRow(item, checkout = false) {
    const cartProduct = productsById.get(item.productId) || product;
    const row = document.createElement('article');
    row.className = checkout ? 'checkout-item' : 'cart-item';

    const image = document.createElement('img');
    image.src = imagePath(cartProduct, item.color);
    image.alt = `${cartProduct.name}, cor ${item.color}`;
    image.loading = 'lazy';
    row.append(image);

    const details = document.createElement('div');
    details.className = checkout ? 'checkout-item-details' : 'cart-item-details';
    const name = document.createElement('h2');
    name.textContent = cartProduct.name;
    const variant = document.createElement('p');
    variant.textContent = `Cor: ${item.color} · Tamanho: ${item.size}`;
    const unitPrice = document.createElement('p');
    unitPrice.className = 'cart-item-price';
    unitPrice.textContent = `${formatMoney(cartProduct.price)} cada`;
    details.append(name, variant, unitPrice);
    row.append(details);

    if (checkout) {
      const quantity = document.createElement('span');
      quantity.className = 'checkout-item-quantity';
      quantity.textContent = `${item.quantity} × ${formatMoney(cartProduct.price)}`;
      row.append(quantity);
      return row;
    }

    const key = `${item.productId}|${item.color}|${item.size}`;
    const controls = document.createElement('div');
    controls.className = 'cart-item-controls';
    const quantityLabel = document.createElement('span');
    quantityLabel.textContent = 'Quantidade';
    const quantityControl = document.createElement('div');
    quantityControl.className = 'quantity-control';
    const decrease = document.createElement('button');
    decrease.type = 'button';
    decrease.dataset.cartAction = 'decrease';
    decrease.dataset.cartKey = key;
    decrease.setAttribute('aria-label', `Diminuir quantidade de ${cartProduct.name}, ${item.color}, tamanho ${item.size}`);
    decrease.textContent = '−';
    const quantity = document.createElement('output');
    quantity.textContent = String(item.quantity);
    const increase = document.createElement('button');
    increase.type = 'button';
    increase.dataset.cartAction = 'increase';
    increase.dataset.cartKey = key;
    increase.setAttribute('aria-label', `Aumentar quantidade de ${cartProduct.name}, ${item.color}, tamanho ${item.size}`);
    increase.textContent = '+';
    quantityControl.append(decrease, quantity, increase);
    const lineTotal = document.createElement('strong');
    lineTotal.className = 'cart-line-total';
    lineTotal.textContent = formatMoney(cartProduct.price * item.quantity);
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'cart-remove';
    remove.dataset.cartAction = 'remove';
    remove.dataset.cartKey = key;
    remove.textContent = 'Remover';
    controls.append(quantityLabel, quantityControl, lineTotal, remove);
    row.append(controls);
    return row;
  }

  function cartTotal(cart) {
    return cart.reduce((total, item) => total + (productsById.get(item.productId)?.price || 0) * item.quantity, 0);
  }

  function estimateDistanceKm(latitude, longitude) {
    const radians = (degrees) => degrees * Math.PI / 180;
    const latitudeDelta = radians(latitude - shippingOrigin.latitude);
    const longitudeDelta = radians(longitude - shippingOrigin.longitude);
    const originLatitude = radians(shippingOrigin.latitude);
    const destinationLatitude = radians(latitude);
    const haversine = Math.sin(latitudeDelta / 2) ** 2
      + Math.cos(originLatitude) * Math.cos(destinationLatitude)
      * Math.sin(longitudeDelta / 2) ** 2;
    const directDistanceKm = 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    return Math.round(directDistanceKm * 1.25);
  }

  function estimatedShippingCents(distanceKm) {
    return Math.round((990 + distanceKm * 1.8) / 10) * 10;
  }

  function readOrder() {
    try {
      return JSON.parse(sessionStorage.getItem(orderKey) || 'null');
    } catch {
      return null;
    }
  }

  function addBusinessDays(startDate, businessDays) {
    const result = new Date(startDate);
    result.setHours(12, 0, 0, 0);
    let remainingDays = businessDays;
    while (remainingDays > 0) {
      result.setDate(result.getDate() + 1);
      if (result.getDay() !== 0 && result.getDay() !== 6) remainingDays -= 1;
    }
    return result;
  }

  function estimatedRouteHubs(state) {
    const capitals = {
      AC: 'Rio Branco', AL: 'Maceió', AP: 'Macapá', AM: 'Manaus', BA: 'Salvador',
      CE: 'Fortaleza', DF: 'Brasília', ES: 'Vitória', GO: 'Goiânia', MA: 'São Luís',
      MT: 'Cuiabá', MS: 'Campo Grande', MG: 'Belo Horizonte', PA: 'Belém', PB: 'João Pessoa',
      PR: 'Curitiba', PE: 'Recife', PI: 'Teresina', RJ: 'Rio de Janeiro', RN: 'Natal',
      RS: 'Porto Alegre', RO: 'Porto Velho', RR: 'Boa Vista', SC: 'Florianópolis',
      SP: 'São Paulo', SE: 'Aracaju', TO: 'Palmas'
    };
    if (state === 'PR') return [];
    if (state === 'RJ') return ['Centro de transferência interestadual · São Paulo (SP)'];
    if (state === 'SP') return ['Centro de distribuição regional · São Paulo (SP)'];
    if (state === 'SC') return ['Centro de distribuição regional · Joinville (SC)'];
    if (state === 'RS') return ['Centro de distribuição regional · Porto Alegre (RS)'];
    if (['MG', 'ES'].includes(state)) {
      return ['Centro de transferência interestadual · São Paulo (SP)', `Centro regional · ${capitals[state]} (${state})`];
    }
    return [
      'Centro de transferência interestadual · São Paulo (SP)',
      `Centro de distribuição regional · ${capitals[state] || state} (${state})`
    ];
  }

  function createTrackingEvents(order, hubs, estimatedDays) {
    const orderedAt = new Date(order.createdAt || Date.now());
    const event = (title, description, days, status) => ({
      title,
      description,
      date: addBusinessDays(orderedAt, days),
      status
    });
    const events = [
      event('Pedido demonstrativo recebido', 'Registro criado nesta loja para visualizar as etapas previstas.', 0, 'complete'),
      event('Preparando pedido', 'Separação da peça e preparação para uma possível expedição.', 0, 'current'),
      event('Vistoria e embalagem', 'Etapa prevista de conferência da peça e proteção da embalagem.', 1, 'future'),
      event('Coleta da transportadora', 'Previsão de retirada no centro de expedição de Curitiba (PR).', 2, 'future')
    ];

    hubs.forEach((hub, index) => {
      events.push(event(`Transferência prevista · ${hub}`, 'Passagem estimada por um centro logístico no caminho até o destino.', 3 + index, 'future'));
    });

    const localUnitDay = Math.max(3 + hubs.length, estimatedDays - 1);
    events.push(event(
      'Chegada prevista à unidade de entrega',
      `Triagem local estimada antes da última etapa até ${order.shipping.city} (${order.shipping.state}).`,
      localUnitDay,
      'future'
    ));
    events.push(event(
      'Entrega estimada',
      'Janela aproximada. O dia pode variar conforme a operação da transportadora.',
      estimatedDays,
      'future'
    ));
    return events;
  }

  function renderTracking() {
    const trackingPage = document.querySelector('[data-tracking-order]');
    if (!trackingPage) return;
    const order = readOrder();
    if (!order?.shipping || !Array.isArray(order.items)) {
      trackingPage.querySelector('[data-tracking-empty]')?.classList.remove('is-hidden');
      trackingPage.querySelector('[data-tracking-details]')?.classList.add('is-hidden');
      return;
    }

    const hubs = estimatedRouteHubs(order.shipping.state);
    const distanceKm = Number(order.shipping.distanceKm) || 0;
    const estimatedDays = Math.min(14, Math.max(4, Math.ceil(distanceKm / 500) + 3, hubs.length + 4));
    const orderedAt = new Date(order.createdAt || Date.now());
    const formatDate = (date, options = {}) => new Intl.DateTimeFormat('pt-BR', {
      weekday: 'short', day: '2-digit', month: 'long', ...options
    }).format(date);

    trackingPage.querySelector('[data-tracking-number]').textContent = order.number;
    trackingPage.querySelector('[data-tracking-destination]').textContent = `${order.shipping.city} (${order.shipping.state})`;
    trackingPage.querySelector('[data-tracking-update]').textContent = `Etapa atual simulada · pedido recebido em ${formatDate(orderedAt, { year: 'numeric' })}.`;
    const earliestDays = Math.max(1, estimatedDays - 1);
    const latestDays = estimatedDays + 1;
    const startDate = addBusinessDays(orderedAt, earliestDays);
    const endDate = addBusinessDays(orderedAt, latestDays);
    trackingPage.querySelector('[data-delivery-start]').textContent = formatDate(startDate);
    trackingPage.querySelector('[data-delivery-start]').dateTime = startDate.toISOString();
    trackingPage.querySelector('[data-delivery-end]').textContent = formatDate(endDate);
    trackingPage.querySelector('[data-delivery-end]').dateTime = endDate.toISOString();
    trackingPage.querySelector('[data-delivery-duration]').textContent = `Previsão de ${earliestDays} a ${latestDays} dias úteis após a confirmação demonstrativa.`;

    const routeStops = [
      'Expedição estimada · Curitiba (PR)',
      ...hubs,
      `Unidade de entrega prevista · ${order.shipping.city} (${order.shipping.state})`
    ];
    const routeList = trackingPage.querySelector('[data-route-stops]');
    routeList.replaceChildren(...routeStops.map((label, index) => {
      const stop = document.createElement('li');
      const number = document.createElement('span');
      number.className = 'route-stop-number';
      number.textContent = String(index + 1).padStart(2, '0');
      const name = document.createElement('span');
      name.textContent = label;
      stop.append(number, name);
      return stop;
    }));

    const timeline = trackingPage.querySelector('[data-tracking-timeline]');
    const statusLabels = { complete: 'Registrado', current: 'Etapa atual simulada', future: 'Previsto' };
    const events = createTrackingEvents(order, hubs, estimatedDays);
    timeline.replaceChildren(...events.map((item) => {
      const entry = document.createElement('li');
      entry.className = `tracking-event is-${item.status}`;
      const marker = document.createElement('span');
      marker.className = 'tracking-event-marker';
      marker.setAttribute('aria-hidden', 'true');
      const content = document.createElement('div');
      content.className = 'tracking-event-content';
      const heading = document.createElement('div');
      heading.className = 'tracking-event-heading';
      const title = document.createElement('h3');
      title.textContent = item.title;
      const status = document.createElement('span');
      status.className = 'tracking-event-label';
      status.textContent = statusLabels[item.status];
      const date = document.createElement('time');
      date.dateTime = item.date.toISOString();
      date.textContent = formatDate(item.date, { year: 'numeric' });
      const description = document.createElement('p');
      description.textContent = item.description;
      heading.append(title, status);
      content.append(heading, date, description);
      entry.append(marker, content);
      return entry;
    }));
    trackingPage.querySelector('[data-tracking-details]')?.classList.remove('is-hidden');
  }

  function renderShipping() {
    const postalCode = document.querySelector('[name="postal-code"]');
    const normalizedPostalCode = postalCode?.value.replace(/\D/g, '');
    const quoteMatchesPostalCode = shippingQuote && shippingQuote.postalCode === normalizedPostalCode;
    if (shippingQuote && !quoteMatchesPostalCode) shippingQuote = null;

    const shippingTotal = document.querySelector('[data-shipping-total]');
    if (shippingTotal) {
      shippingTotal.textContent = quoteMatchesPostalCode
        ? formatMoney(shippingQuote.shippingCents)
        : 'Calcule pelo CEP';
    }

    const checkoutTotal = document.querySelector('[data-checkout-total]');
    if (checkoutTotal) {
      checkoutTotal.textContent = quoteMatchesPostalCode
        ? formatMoney(cartTotal(readCart()) + shippingQuote.shippingCents)
        : 'Aguardando CEP';
    }

    const shippingSummary = document.querySelector('[data-shipping-summary]');
    if (shippingSummary) {
      shippingSummary.textContent = quoteMatchesPostalCode
        ? `Destino: ${shippingQuote.city} - ${shippingQuote.state}. Distância estimada: ${shippingQuote.distanceKm.toLocaleString('pt-BR')} km desde Curitiba (PR). Valor demonstrativo, não é cotação de transportadora.`
        : 'Estimativa pela distância desde Curitiba (PR); o valor não substitui a cotação de uma transportadora.';
    }

    const submitButton = document.querySelector('.checkout-submit');
    if (submitButton) submitButton.disabled = !quoteMatchesPostalCode || readCart().length === 0;
  }

  function renderCart() {
    const list = document.querySelector('[data-cart-items]');
    if (!list) return;
    const cart = readCart();
    const isEmpty = cart.length === 0;
    list.replaceChildren(...cart.map((item) => makeCartRow(item)));
    document.querySelector('[data-cart-content]')?.classList.toggle('is-hidden', isEmpty);
    document.querySelector('[data-cart-empty]')?.classList.toggle('is-hidden', !isEmpty);
    const subtotal = document.querySelector('[data-cart-subtotal]');
    if (subtotal) subtotal.textContent = formatMoney(cartTotal(cart));
  }

  function renderCheckout() {
    const list = document.querySelector('[data-checkout-items]');
    if (!list) return;
    const cart = readCart();
    list.replaceChildren(...cart.map((item) => makeCartRow(item, true)));
    const total = formatMoney(cartTotal(cart));
    document.querySelectorAll('[data-checkout-total]').forEach((element) => {
      element.textContent = total;
    });
    const subtotal = document.querySelector('[data-checkout-subtotal]');
    if (subtotal) subtotal.textContent = total;
    document.querySelector('[data-checkout-empty]')?.classList.toggle('is-hidden', cart.length > 0);
    document.querySelector('[data-checkout-form]')?.classList.toggle('is-hidden', cart.length === 0);
    renderShipping();
  }

  function initializeProductListing() {
    const list = document.querySelector('[data-product-list]');
    if (!list) return;
    const filterPanel = document.querySelector('[data-filter-panel]');
    const colorFilters = filterPanel.querySelector('[data-color-filters]');
    const sizeFilters = filterPanel.querySelector('[data-size-filters]');
    const createFilter = (container, name, value) => {
      const label = document.createElement('label');
      const input = document.createElement('input');
      input.type = 'checkbox';
      input.name = name;
      input.value = value;
      label.append(input, document.createTextNode(` ${value}`));
      container.append(label);
    };
    const colors = [...new Set(products.flatMap((item) => Object.keys(item.images)
      .filter((color) => !item.unavailableColors.includes(color))))];
    colors.forEach((color) => createFilter(colorFilters, 'filter-color', color));
    const sizes = availableSizes.filter((size) => products.some((item) => Object.keys(item.images)
      .some((color) => !item.unavailableColors.includes(color) && productSizes(item, color).includes(size))));
    sizes.forEach((size) => createFilter(sizeFilters, 'filter-size', size));

    const renderFilteredProducts = () => {
      const selectedColors = [...filterPanel.querySelectorAll('[name="filter-color"]:checked')].map((input) => input.value);
      const selectedSizes = [...filterPanel.querySelectorAll('[name="filter-size"]:checked')].map((input) => input.value);
      const minimum = Number(filterPanel.querySelector('#price-min').value) || 0;
      const maximumValue = filterPanel.querySelector('#price-max').value;
      const maximum = maximumValue === '' ? Infinity : Number(maximumValue);
      const matches = products.filter((item) => {
        if (item.price < minimum * 100 || item.price > maximum * 100) return false;
        return Object.keys(item.images).some((color) => !item.unavailableColors.includes(color)
          && (!selectedColors.length || selectedColors.includes(color))
          && (!selectedSizes.length || selectedSizes.some((size) => productSizes(item, color).includes(size))));
      });
      const count = document.querySelector('[data-product-count]');
      if (count) count.textContent = `${matches.length} ${matches.length === 1 ? 'produto' : 'produtos'}`;
      if (matches.length) {
        list.replaceChildren(...matches.map((item) => createCatalogCard(item)));
      } else {
        const emptyMessage = document.createElement('p');
        emptyMessage.className = 'product-list-empty';
        emptyMessage.textContent = 'Nenhum vestido encontrado com esses filtros.';
        list.replaceChildren(emptyMessage);
      }
    };

    filterPanel.addEventListener('change', renderFilteredProducts);
    filterPanel.addEventListener('input', (event) => {
      if (event.target.matches('#price-min, #price-max')) renderFilteredProducts();
    });
    filterPanel.querySelector('[data-clear-filters]').addEventListener('click', () => {
      filterPanel.querySelectorAll('input[type="checkbox"]').forEach((input) => { input.checked = false; });
      filterPanel.querySelectorAll('input[type="number"]').forEach((input) => { input.value = ''; });
      renderFilteredProducts();
    });
    renderFilteredProducts();
  }

  document.querySelector('[data-newsletter-form]')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const email = document.querySelector('[data-newsletter-form] [name="email"]').value.trim();
    const subject = encodeURIComponent('Inscrição na lista FSI Studio');
    const body = encodeURIComponent(`Olá, gostaria de receber novidades da FSI Studio.\n\nMeu e-mail: ${email}`);
    const feedback = document.querySelector('[data-newsletter-feedback]');
    if (feedback) feedback.textContent = 'Seu aplicativo de e-mail foi aberto. Envie a mensagem para concluir a inscrição.';
    window.location.href = `https://mail.google.com/mail/?view=cm&fs=1&to=fashionstyleinst0@gmail.com&su=${subject}&body=${body}`;
  });

  function renderProductSizes(color, preferredSize = '') {
    const sizeList = document.querySelector('.size-options');
    if (!sizeList) return;
    const sizes = productSizes(product, color);
    const selectedSize = sizes.includes(preferredSize) ? preferredSize : sizes[0];
    sizeList.replaceChildren(...sizes.map((size) => {
      const button = document.createElement('button');
      button.className = `size-option${size === selectedSize ? ' is-selected' : ''}`;
      button.type = 'button';
      button.dataset.size = size;
      button.textContent = size;
      return button;
    }));
    const selectedLabel = document.querySelector('#selected-size');
    if (selectedLabel) selectedLabel.textContent = selectedSize;
  }

  function selectProductColor(color) {
    const image = imagePath(product, color);
    const mainImage = document.querySelector('#main-product-image');
    if (mainImage) {
      mainImage.src = image;
      mainImage.alt = `${product.name}, cor ${color}`;
    }
    const selectedColor = document.querySelector('#selected-color');
    if (selectedColor) selectedColor.textContent = color;
    document.querySelectorAll('[data-product-color]').forEach((button) => {
      button.classList.toggle('is-selected', button.dataset.productColor === color);
    });
    renderProductSizes(color, document.querySelector('#selected-size')?.textContent.trim() || '');
  }

  function initializeProductDetail() {
    if (!document.querySelector('.product-detail')) return;
    const colors = Object.keys(product.images);
    const firstAvailableColor = colors.find((color) => !product.unavailableColors.includes(color));
    const thumbnailList = document.querySelector('.product-thumbnails');
    const colorList = document.querySelector('.color-options');
    const mainImage = document.querySelector('#main-product-image');
    const title = document.querySelector('.product-info h1');
    if (title) title.textContent = product.name;
    document.title = `${product.name} | FSI Studio`;
    const breadcrumb = document.querySelector('.product-breadcrumb');
    if (breadcrumb?.lastChild) breadcrumb.lastChild.textContent = ` / ${product.name}`;
    if (mainImage) {
      mainImage.src = imagePath(product, firstAvailableColor);
      mainImage.alt = `${product.name}, cor ${firstAvailableColor}`;
    }
    if (thumbnailList) thumbnailList.replaceChildren(...colors.map((color) => {
      const unavailable = product.unavailableColors.includes(color);
      const button = document.createElement('button');
      button.className = `product-thumb${unavailable ? ' is-unavailable' : ''}`;
      button.type = 'button';
      button.dataset.productColor = color;
      button.setAttribute('aria-label', `${unavailable ? 'Indisponível: ' : 'Ver '}${product.name}, ${color}`);
      button.disabled = unavailable;
      const image = document.createElement('img');
      image.src = imagePath(product, color);
      image.alt = '';
      button.append(image);
      if (unavailable) {
        const label = document.createElement('span');
        label.textContent = 'Indisponível';
        button.append(label);
      }
      return button;
    }));
    if (colorList) colorList.replaceChildren(...colors.map((color) => {
      const unavailable = product.unavailableColors.includes(color);
      const button = document.createElement('button');
      button.className = `color-option${unavailable ? ' is-unavailable' : ''}`;
      button.type = 'button';
      button.dataset.productColor = color;
      button.setAttribute('aria-label', `${unavailable ? 'Indisponível: ' : 'Selecionar '}${color}`);
      button.disabled = unavailable;
      const image = document.createElement('img');
      image.src = imagePath(product, color);
      image.alt = '';
      button.append(image);
      if (unavailable) {
        const label = document.createElement('span');
        label.textContent = 'Indisponível';
        button.append(label);
      }
      return button;
    }));
    const price = document.querySelector('.product-price-large');
    if (price) price.textContent = formatMoney(product.price);
    const colorLabel = document.querySelector('#selected-color');
    if (colorLabel) colorLabel.textContent = firstAvailableColor;
    const description = document.querySelector('.product-detail-note');
    if (description) description.textContent = `${product.name} com modelagem confortável e acabamento pensado para acompanhar diferentes ocasiões.`;
    const productDescription = document.querySelector('.product-details p');
    if (productDescription) productDescription.textContent = `${product.name}: ${product.note}. Confira as opções de cor e tamanho disponíveis para este modelo.`;
    selectProductColor(firstAvailableColor);
  }

  async function calculateShipping() {
    const postalCode = document.querySelector('[name="postal-code"]');
    const button = document.querySelector('[data-calculate-shipping]');
    const feedback = document.querySelector('[data-shipping-feedback]');
    const normalizedPostalCode = postalCode?.value.replace(/\D/g, '') || '';
    const requestId = ++shippingRequestId;
    shippingQuote = null;
    renderShipping();

    if (!/^\d{8}$/.test(normalizedPostalCode)) {
      if (feedback) feedback.textContent = 'Digite um CEP válido com 8 números.';
      postalCode?.focus();
      return;
    }

    if (button) {
      button.disabled = true;
      button.textContent = 'Calculando...';
    }
    if (feedback) feedback.textContent = 'Consultando o CEP e estimando a distância...';

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(`https://brasilapi.com.br/api/cep/v2/${normalizedPostalCode}`, {
        signal: controller.signal
      });
      if (!response.ok) throw new Error('CEP não encontrado');
      const address = await response.json();
      const latitude = Number(address.location?.coordinates?.latitude);
      const longitude = Number(address.location?.coordinates?.longitude);
      if (!address.city || !address.state || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        throw new Error('Localização indisponível');
      }
      if (requestId !== shippingRequestId) return;

      const cityField = document.querySelector('[name="city"]');
      const stateField = document.querySelector('[name="region"]');
      const selectedState = findState(stateField?.value || '');
      if (stateField?.value && selectedState?.uf !== address.state) {
        if (feedback) feedback.textContent = 'O CEP informado não pertence ao estado selecionado. Confira o estado ou o CEP.';
        return;
      }
      if (cityField?.value && normalizeLocation(cityField.value) !== normalizeLocation(address.city)) {
        if (feedback) feedback.textContent = `O CEP informado corresponde a ${address.city} - ${address.state}. Confira a cidade ou o CEP.`;
        return;
      }

      const distanceKm = estimateDistanceKm(latitude, longitude);
      shippingQuote = {
        postalCode: normalizedPostalCode,
        city: address.city,
        state: address.state,
        distanceKm,
        shippingCents: estimatedShippingCents(distanceKm)
      };
      const stateName = brazilStates.find((state) => state.uf === address.state)?.name || address.state;
      if (cityField) cityField.value = address.city;
      if (stateField) stateField.value = stateName;
      const stateFeedback = document.querySelector('[data-state-feedback]');
      if (stateFeedback) stateFeedback.textContent = `Estado selecionado: ${stateName}.`;
      void loadCitiesForState(address.state, address.city);
      if (feedback) feedback.textContent = `Frete estimado para ${address.city} - ${address.state}: ${formatMoney(shippingQuote.shippingCents)}.`;
      renderShipping();
    } catch (error) {
      if (requestId !== shippingRequestId) return;
      if (feedback) {
        feedback.textContent = error.name === 'AbortError'
          ? 'A consulta demorou demais. Tente calcular novamente.'
          : 'Não foi possível localizar esse CEP. Confira os números e tente novamente.';
      }
    } finally {
      clearTimeout(timeout);
      if (requestId === shippingRequestId && button) {
        button.disabled = false;
        button.textContent = 'Calcular frete';
      }
    }
  }

  document.querySelector('[data-product-add]')?.addEventListener('click', () => {
    const color = document.querySelector('#selected-color')?.textContent.trim();
    const size = document.querySelector('#selected-size')?.textContent.trim();
    if (!Object.hasOwn(product.images, color) || !productSizes(product, color).includes(size)) {
      showFeedback('Selecione uma cor e um tamanho disponíveis.');
      return;
    }
    const cart = readCart();
    const existingItem = cart.find((item) => item.productId === product.id && item.color === color && item.size === size);
    if (existingItem) {
      showCartToast('Erro ao adicionar ao carrinho: este item já está no carrinho.');
      return;
    }
    cart.push({ productId: product.id, color, size, quantity: 1 });
    writeCart(cart);
    renderMiniCart(cart);
    openCartDrawer();
    showFeedback(`${product.name} adicionado ao carrinho.`);
  });

  document.querySelector('[data-cart-items]')?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-cart-action]');
    if (!button) return;
    const [productId, color, size] = button.dataset.cartKey.split('|');
    const cart = readCart();
    const item = cart.find((entry) => entry.productId === productId && entry.color === color && entry.size === size);
    if (!item) return;
    if (button.dataset.cartAction === 'remove' || (button.dataset.cartAction === 'decrease' && item.quantity === 1)) {
      writeCart(cart.filter((entry) => entry !== item));
      return;
    }
    if (button.dataset.cartAction === 'decrease') item.quantity -= 1;
    if (button.dataset.cartAction === 'increase') item.quantity = Math.min(item.quantity + 1, 99);
    writeCart(cart);
  });

  document.querySelector('[data-calculate-shipping]')?.addEventListener('click', calculateShipping);
  document.querySelector('[name="postal-code"]')?.addEventListener('input', () => {
    shippingRequestId += 1;
    shippingQuote = null;
    const feedback = document.querySelector('[data-shipping-feedback]');
    if (feedback) feedback.textContent = 'Calcule novamente após alterar o CEP.';
    const button = document.querySelector('[data-calculate-shipping]');
    if (button) {
      button.disabled = false;
      button.textContent = 'Calcular frete';
    }
    renderShipping();
  });

  document.querySelector('[data-checkout-form]')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const cart = readCart();
    if (!cart.length) return;
    const postalCode = document.querySelector('[name="postal-code"]')?.value.replace(/\D/g, '');
    const selectedState = findState(document.querySelector('[name="region"]')?.value || '');
    const selectedCity = document.querySelector('[name="city"]')?.value || '';
    if (!shippingQuote || shippingQuote.postalCode !== postalCode
      || selectedState?.uf !== shippingQuote.state
      || normalizeLocation(selectedCity) !== normalizeLocation(shippingQuote.city)) {
      const feedback = document.querySelector('[data-shipping-feedback]');
      if (feedback) feedback.textContent = 'Confira o estado, a cidade e o CEP e calcule o frete novamente.';
      document.querySelector('[data-calculate-shipping]')?.focus();
      renderShipping();
      return;
    }
    const selectedPayment = document.querySelector('input[name="payment-method"]:checked');
    const order = {
      number: `FSI-${Date.now().toString().slice(-8)}`,
      createdAt: new Date().toISOString(),
      items: cart,
      subtotal: cartTotal(cart),
      shipping: {
        city: shippingQuote.city,
        state: shippingQuote.state,
        distanceKm: shippingQuote.distanceKm,
        shippingCents: shippingQuote.shippingCents
      },
      total: cartTotal(cart) + shippingQuote.shippingCents,
      payment: selectedPayment?.value || 'Pix (simulação)'
    };
    try {
      sessionStorage.setItem(orderKey, JSON.stringify(order));
    } catch {
      showFeedback('Não foi possível concluir a demonstração neste navegador.');
      return;
    }
    writeCart([]);
    window.location.href = 'pedido-confirmado.html';
  });

  function renderConfirmation() {
    const confirmation = document.querySelector('[data-order-confirmation]');
    if (!confirmation) return;
    const order = readOrder();
    if (!order || !Array.isArray(order.items)) {
      confirmation.querySelector('[data-no-order]')?.classList.remove('is-hidden');
      confirmation.querySelector('[data-order-details]')?.classList.add('is-hidden');
      return;
    }
    confirmation.querySelector('[data-order-number]').textContent = order.number;
    confirmation.querySelector('[data-order-payment]').textContent = order.payment;
    confirmation.querySelector('[data-order-subtotal]').textContent = formatMoney(order.subtotal);
    confirmation.querySelector('[data-order-total]').textContent = formatMoney(order.total);
    confirmation.querySelector('[data-order-shipping]').textContent = formatMoney(order.shipping.shippingCents);
    confirmation.querySelector('[data-order-destination]').textContent = `${order.shipping.city} - ${order.shipping.state} · ${order.shipping.distanceKm.toLocaleString('pt-BR')} km estimados`;
    const list = confirmation.querySelector('[data-order-items]');
    list.replaceChildren(...order.items.map((item) => makeCartRow(item, true)));
    confirmation.querySelector('[data-order-details]')?.classList.remove('is-hidden');
  }

  document.addEventListener('click', (event) => {
    const drawer = document.querySelector('[data-cart-drawer]');
    if (event.target.closest('[data-close-cart]') || event.target === drawer) {
      closeCartDrawer();
      return;
    }
    const removeButton = event.target.closest('[data-mini-cart-remove]');
    if (removeButton) {
      const [productId, color, size] = removeButton.dataset.cartKey.split('|');
      const cart = readCart().filter((item) => !(item.productId === productId && item.color === color && item.size === size));
      writeCart(cart);
      renderMiniCart(cart);
      return;
    }
    const colorButton = event.target.closest('[data-product-color]');
    if (colorButton && !colorButton.disabled) {
      selectProductColor(colorButton.dataset.productColor);
      return;
    }
    const sizeButton = event.target.closest('.size-option');
    if (sizeButton && !sizeButton.disabled) {
      document.querySelectorAll('.size-option').forEach((button) => button.classList.remove('is-selected'));
      sizeButton.classList.add('is-selected');
      document.querySelector('#selected-size').textContent = sizeButton.dataset.size;
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !document.querySelector('[data-cart-drawer]')?.hidden) closeCartDrawer();
  });

  initializeProductListing();
  initializeStoreSearch();
  initializeProductDetail();
  initializeRecommendations();
  initializeAddressSuggestions();
  updateBagCount();
  renderCart();
  renderCheckout();
  renderConfirmation();
  renderTracking();
  window.addEventListener('storage', () => {
    updateBagCount();
    renderCart();
    renderCheckout();
  });
})();
