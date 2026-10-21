(() => {
  const apiBase = (window.FSI_API_BASE_URL || '').replace(/\/$/, '');
  const login = document.querySelector('[data-admin-login]');
  const workspace = document.querySelector('[data-admin-workspace]');
  const loginForm = document.querySelector('[data-login-form]');
  const loginFeedback = document.querySelector('[data-login-feedback]');
  const adminFeedback = document.querySelector('[data-admin-feedback]');
  const orderList = document.querySelector('[data-admin-orders]');
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

  document.querySelector('[data-logout]').addEventListener('click', () => {
    authorization = '';
    orderList.replaceChildren();
    workspace.hidden = true;
    login.hidden = false;
  });
})();
