(() => {
  const apiBase = (window.FSI_API_BASE_URL || '').replace(/\/$/, '');
  const login = document.querySelector('[data-admin-login]');
  const workspace = document.querySelector('[data-admin-workspace]');
  const loginForm = document.querySelector('[data-login-form]');
  const loginFeedback = document.querySelector('[data-login-feedback]');
  const adminFeedback = document.querySelector('[data-admin-feedback]');
  const orderList = document.querySelector('[data-admin-orders]');
  const accountSearchForm = document.querySelector('[data-account-search-form]');
  const accountList = document.querySelector('[data-admin-accounts]');
  const temporaryPasswordResult = document.querySelector('[data-temporary-password-result]');
  let authorization = '';

  async function request(path, options = {}) {
    let response;
    try {
      response = await fetch(`${apiBase}${path}`, {
        ...options,
        headers: {
          Accept: 'application/json',
          ...(options.body ? { 'Content-Type': 'application/json' } : {}),
          ...(authorization ? { Authorization: authorization } : {}),
          ...options.headers
        }
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
  }

  function formatMoney(cents) {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
  }

  function textElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    element.textContent = text;
    return element;
  }

  function renderAdminAccounts(accounts) {
    if (!accounts.length) {
      accountList.replaceChildren(textElement('p', 'admin-hint', 'Nenhuma conta encontrada para essa busca.'));
      return;
    }

    accountList.replaceChildren(...accounts.map(account => {
      const row = document.createElement('article');
      row.className = 'admin-account-row';
      const identity = document.createElement('div');
      identity.append(
        textElement('strong', '', account.fullName),
        textElement('small', '', [account.email, account.phone].filter(Boolean).join(' · ') || 'Sem contato cadastrado')
      );
      const reset = document.createElement('button');
      reset.type = 'button';
      reset.className = 'admin-secondary-button';
      reset.textContent = 'Gerar senha temporária';
      reset.addEventListener('click', () => issueTemporaryPassword(account, reset));
      row.append(identity, reset);
      return row;
    }));
  }

  function showTemporaryPassword(account, result) {
    const expiresAt = new Date(result.expiresAt);
    const message = textElement('p', '',
      `Senha temporária de ${account.fullName}. Ela expira em ${Number.isNaN(expiresAt.valueOf()) ? '24 horas' : expiresAt.toLocaleString('pt-BR')}. Mostre ou entregue diretamente ao cliente; não a envie em canais públicos.`);
    const password = textElement('code', 'admin-temp-password', result.temporaryPassword);
    const actions = document.createElement('div');
    actions.className = 'admin-temp-password-actions';
    const copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'admin-secondary-button';
    copy.textContent = 'Copiar senha';
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(result.temporaryPassword);
        adminFeedback.textContent = 'Senha copiada. Entregue-a diretamente ao cliente.';
      } catch {
        adminFeedback.textContent = 'Não foi possível copiar automaticamente. Selecione a senha exibida e copie manualmente.';
      }
    });
    const hide = document.createElement('button');
    hide.type = 'button';
    hide.className = 'admin-secondary-button';
    hide.textContent = 'Ocultar senha';
    hide.addEventListener('click', () => {
      temporaryPasswordResult.replaceChildren();
      temporaryPasswordResult.hidden = true;
    });
    actions.append(copy, hide);
    temporaryPasswordResult.replaceChildren(message, password, actions);
    temporaryPasswordResult.hidden = false;
  }

  async function issueTemporaryPassword(account, button) {
    const contact = account.email || account.phone || 'sem contato';
    if (!window.confirm(
      `Emitir uma nova senha temporária para ${account.fullName} (${contact})? Isso encerra as sessões atuais da conta. Confirme a identidade do cliente antes de entregar a senha.`
    )) return;
    button.disabled = true;
    temporaryPasswordResult.replaceChildren();
    temporaryPasswordResult.hidden = true;
    adminFeedback.textContent = 'Gerando senha temporária...';
    try {
      const result = await request(
        `/api/admin/accounts/${encodeURIComponent(account.id)}/temporary-password`,
        { method: 'POST' }
      );
      showTemporaryPassword(account, result);
      button.textContent = 'Senha emitida';
      adminFeedback.textContent = 'Senha temporária emitida. Ela será exibida somente nesta tela.';
    } catch (error) {
      button.disabled = false;
      adminFeedback.textContent = error.message;
    }
  }

  async function searchAccounts(query) {
    accountList.replaceChildren();
    temporaryPasswordResult.replaceChildren();
    temporaryPasswordResult.hidden = true;
    adminFeedback.textContent = 'Buscando contas...';
    try {
      const accounts = await request(`/api/admin/accounts?query=${encodeURIComponent(query)}`);
      renderAdminAccounts(accounts);
      adminFeedback.textContent = `${accounts.length} conta(s) encontrada(s).`;
    } catch (error) {
      adminFeedback.textContent = error.message;
    }
  }

  function renderOrders(orders) {
    if (!orders.length) {
      orderList.replaceChildren(textElement('p', 'admin-hint', 'Nenhum pedido encontrado.'));
      return;
    }
    const labels = {
      PROCESSING: 'Preparando Pix',
      PENDING: 'Aguardando pagamento',
      RECEIVED: 'Pagamento confirmado',
      RECEIVED_REVIEW: 'Pagamento recebido; revisar estoque',
      PAYMENT_FAILED: 'Pix não gerado'
    };
    orderList.replaceChildren(...orders.map(order => {
      const card = document.createElement('article');
      card.className = 'admin-order-card';
      const heading = document.createElement('div');
      heading.className = 'admin-order-heading';
      heading.append(
        textElement('h3', '', order.orderNumber),
        textElement('span', `admin-order-status status-${order.paymentStatus.toLowerCase()}`,
          labels[order.paymentStatus] || 'Status desconhecido')
      );
      const created = new Date(order.createdAt);
      const customerSummary = `${order.customerName} · ${order.customerEmail} · ${order.customerPhone}`;
      const accountSummary = order.account
        ? `Conta vinculada: ${order.account.fullName} · ${order.account.email || order.account.phone || 'contato indisponível'}`
        : 'Pedido feito como visitante; nenhuma conta vinculada.';
      const account = textElement('p', 'admin-order-account', accountSummary);
      if (order.account) account.dataset.accountLinked = 'true';
      card.append(heading,
        textElement('p', 'admin-order-meta', `Recebido em ${Number.isNaN(created.valueOf()) ? 'data indisponível' : created.toLocaleString('pt-BR')}`),
        textElement('p', '', customerSummary),
        account,
        textElement('p', '', `${order.address}, ${order.addressNumber}${order.complement ? `, ${order.complement}` : ''} · ${order.neighborhood} · ${order.city} - ${order.state} · CEP ${order.postalCode}`));
      const items = document.createElement('ul');
      items.className = 'admin-order-items';
      order.items.forEach(item => items.append(textElement('li', '',
        `${item.quantity} × ${item.productName} (${item.color}, ${item.size}) — ${formatMoney(item.unitPriceCents)}`)));
      card.append(items,
        textElement('p', 'admin-order-total',
          `Subtotal ${formatMoney(order.subtotalCents)} · frete ${formatMoney(order.shippingCents)} · total ${formatMoney(order.totalCents)}`));
      if (order.paymentStatus === 'PENDING') {
        const confirm = document.createElement('button');
        confirm.type = 'button';
        confirm.className = 'store-button';
        confirm.textContent = 'Marcar como pago (já conferido no banco)';
        confirm.addEventListener('click', () => confirmPayment(order, confirm));
        card.append(confirm);
      } else if (order.paymentStatus === 'RECEIVED_REVIEW') {
        card.append(textElement('p', 'admin-orders-warning',
          'O crédito foi confirmado, mas o estoque não pôde ser reservado. Resolva este pedido manualmente antes de preparar o envio.'));
      }
      return card;
    }));
  }

  async function loadOrders() {
    adminFeedback.textContent = 'Carregando pedidos...';
    const orders = await request('/api/admin/orders');
    renderOrders(orders);
    adminFeedback.textContent = `${orders.length} pedido(s) carregado(s).`;
  }

  async function confirmPayment(order, button) {
    const confirmed = window.confirm(
      `Confirme somente depois de verificar no extrato que entrou o valor de ${formatMoney(order.totalCents)} para o pedido ${order.orderNumber}. Comprovante do cliente não é suficiente.`
    );
    if (!confirmed) return;
    button.disabled = true;
    adminFeedback.textContent = `Registrando a confirmação do pedido ${order.orderNumber}...`;
    try {
      const result = await request(`/api/admin/orders/${encodeURIComponent(order.id)}/confirm-payment`, { method: 'POST' });
      await loadOrders();
      adminFeedback.textContent = result.paymentStatus === 'RECEIVED_REVIEW'
        ? `Pagamento ${order.orderNumber} confirmado, mas o pedido precisa de revisão manual por estoque.`
        : `Pagamento do pedido ${order.orderNumber} confirmado.`;
    } catch (error) {
      button.disabled = false;
      adminFeedback.textContent = error.message;
    }
  }

  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    const username = loginForm.elements.username.value.trim();
    const password = loginForm.elements.password.value;
    authorization = `Basic ${btoa(`${username}:${password}`)}`;
    loginFeedback.textContent = 'Verificando acesso...';
    try {
      await loadOrders();
      login.hidden = true;
      workspace.hidden = false;
      loginForm.reset();
      loginFeedback.textContent = '';
    } catch (error) {
      authorization = '';
      loginFeedback.textContent = error.message;
    }
  });

  document.querySelector('[data-refresh]').addEventListener('click', async () => {
    try { await loadOrders(); }
    catch (error) { adminFeedback.textContent = error.message; }
  });

  accountSearchForm.addEventListener('submit', event => {
    event.preventDefault();
    void searchAccounts(accountSearchForm.elements.query.value.trim());
  });

  document.querySelector('[data-logout]').addEventListener('click', () => {
    authorization = '';
    orderList.replaceChildren();
    accountList.replaceChildren();
    temporaryPasswordResult.replaceChildren();
    temporaryPasswordResult.hidden = true;
    workspace.hidden = true;
    login.hidden = false;
  });
})();
