(() => {
  const apiBase = (window.FSI_API_BASE_URL || '').replace(/\/$/, '');
  const tokenKey = 'fsi-account-token-v1';
  const root = document.querySelector('.account-page');
  if (!root) return;

  const views = [...root.querySelectorAll('[data-account-view]')];
  const feedback = root.querySelector('[data-account-feedback]');
  const pendingEmail = root.querySelector('[data-pending-email]');
  const orderFeedback = root.querySelector('[data-account-orders-feedback]');
  const orderList = root.querySelector('[data-account-orders]');
  const registerForm = root.querySelector('[data-register-form]');
  let verificationEmail = '';

  function showView(name) {
    views.forEach(view => { view.hidden = view.dataset.accountView !== name; });
    feedback.textContent = '';
    if (orderFeedback) orderFeedback.textContent = '';
  }

  function formData(form) {
    return Object.fromEntries(new FormData(form).entries());
  }

  async function request(path, body, token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${apiBase}${path}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'Não foi possível concluir esta solicitação.');
    return result;
  }

  function storeToken(token) {
    try {
      sessionStorage.setItem(tokenKey, token);
    } catch (error) {
      throw new Error('Não foi possível iniciar uma sessão segura neste navegador. Habilite o armazenamento da sessão e tente novamente.', { cause: error });
    }
  }

  async function enterAccount(auth) {
    if (!auth.token || !auth.account) throw new Error('A API não retornou os dados de sessão esperados.');
    storeToken(auth.token);
    await renderDashboard(auth.account);
  }

  function makeOrderCard(order) {
    const card = document.createElement('article');
    card.className = 'account-order-card';
    const heading = document.createElement('div');
    heading.className = 'account-order-card-heading';
    const number = document.createElement('strong');
    number.textContent = order.orderNumber;
    const status = document.createElement('span');
    status.className = `account-order-status account-order-status-${order.paymentStatus.toLowerCase().replace(/[^a-z_]/g, '')}`;
    const labels = {
      PROCESSING: 'Preparando Pix',
      PENDING: 'Aguardando pagamento',
      RECEIVED: 'Pagamento confirmado',
      PAYMENT_FAILED: 'Pix não gerado',
      RECEIVED_REVIEW: 'Em revisão pela loja'
    };
    status.textContent = labels[order.paymentStatus] || 'Status indisponível';
    heading.append(number, status);
    const summary = document.createElement('p');
    const count = order.items.reduce((total, item) => total + item.quantity, 0);
    summary.textContent = `${count} ${count === 1 ? 'item' : 'itens'} · ${new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(order.totalCents / 100)} · ${order.city} - ${order.state}`;
    const orderedAt = new Date(order.createdAt);
    const date = document.createElement('small');
    date.textContent = Number.isNaN(orderedAt.getTime())
      ? 'Data indisponível'
      : new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(orderedAt);
    const action = document.createElement('a');
    action.className = 'account-order-action';
    const target = order.paymentStatus === 'RECEIVED' ? 'acompanhar-pedido.html' : 'pedido-confirmado.html';
    action.href = `${target}?pedido=${encodeURIComponent(order.id)}`;
    action.textContent = order.paymentStatus === 'RECEIVED' ? 'Acompanhar pedido ↗' : 'Ver pagamento e pedido ↗';
    card.append(heading, summary, date, action);
    return card;
  }

  async function renderDashboard(account) {
    showView('dashboard');
    root.querySelector('[data-account-name]').textContent = account.fullName;
    root.querySelector('[data-account-email]').textContent = account.email;
    root.querySelector('[data-account-phone]').textContent = account.phone;
    orderList.replaceChildren();
    orderFeedback.textContent = 'Carregando suas compras...';
    const token = sessionStorage.getItem(tokenKey);
    try {
      const response = await fetch(`${apiBase}/api/account/orders`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const orders = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(orders.message || 'Não foi possível carregar suas compras.');
      if (!Array.isArray(orders)) throw new Error('A API retornou um histórico de compras inválido.');
      if (!orders.length) {
        orderFeedback.textContent = 'Suas compras feitas com a conta aparecerão aqui. Pedidos antigos com o mesmo e-mail confirmado também podem ser associados.';
        return;
      }
      orderFeedback.textContent = '';
      orderList.replaceChildren(...orders.slice(0, 5).map(makeOrderCard));
    } catch (error) {
      orderFeedback.textContent = error.message;
    }
  }

  function setBusy(form, busy) {
    const button = form.querySelector('button[type="submit"]');
    if (!button) return;
    button.disabled = busy;
    if (busy) {
      button.dataset.previousText = button.textContent;
      button.textContent = 'Aguarde...';
    } else if (button.dataset.previousText) {
      button.textContent = button.dataset.previousText;
      delete button.dataset.previousText;
    }
  }

  async function submit(form, callback) {
    feedback.textContent = '';
    setBusy(form, true);
    try {
      await callback(formData(form), form);
    } catch (error) {
      feedback.textContent = error.message;
    } finally {
      setBusy(form, false);
    }
  }

  root.querySelector('[data-login-form]').addEventListener('submit', event => {
    event.preventDefault();
    void submit(event.currentTarget, async data => {
      const auth = await request('/api/account/login', data);
      await enterAccount(auth);
    });
  });

  root.querySelector('[data-register-form]').addEventListener('submit', event => {
    event.preventDefault();
    void submit(event.currentTarget, async data => {
      const result = await request('/api/account/register', data);
      if (result.session) {
        await enterAccount(result.session);
        feedback.textContent = result.message;
        return;
      }
      verificationEmail = data.email.trim();
      pendingEmail.textContent = verificationEmail;
      showView('verify');
      feedback.textContent = result.message;
    });
  });

  registerForm.querySelectorAll('[name="contactType"]').forEach(choice => {
    choice.addEventListener('change', () => {
      registerForm.querySelectorAll('[data-contact-field]').forEach(field => {
        const active = field.dataset.contactField === choice.value;
        field.hidden = !active;
        const input = field.querySelector('input');
        input.disabled = !active;
        input.required = active;
      });
    });
  });

  root.querySelector('[data-verify-form]').addEventListener('submit', event => {
    event.preventDefault();
    void submit(event.currentTarget, async data => {
      const auth = await request('/api/account/verify-email', { email: verificationEmail, code: data.code });
      await enterAccount(auth);
    });
  });

  root.querySelector('[data-resend-code]').addEventListener('click', async event => {
    const button = event.currentTarget;
    if (!verificationEmail) {
      feedback.textContent = 'Volte ao cadastro e informe seu e-mail novamente.';
      return;
    }
    feedback.textContent = '';
    button.disabled = true;
    try {
      const result = await request('/api/account/verify-email/resend', { email: verificationEmail });
      feedback.textContent = result.message;
    } catch (error) {
      feedback.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  root.querySelector('[data-forgot-form]').addEventListener('submit', event => {
    event.preventDefault();
    void submit(event.currentTarget, async data => {
      await request('/api/account/forgot-password', data);
      const resetEmail = root.querySelector('[data-reset-form] [name="email"]');
      resetEmail.value = data.email.trim();
      showView('reset');
      feedback.textContent = 'Se o e-mail corresponder a uma conta confirmada, o código foi enviado.';
    });
  });

  root.querySelector('[data-reset-form]').addEventListener('submit', event => {
    event.preventDefault();
    void submit(event.currentTarget, async data => {
      const result = await request('/api/account/reset-password', data);
      showView('login');
      feedback.textContent = result.message;
    });
  });

  root.addEventListener('click', event => {
    const viewButton = event.target.closest('[data-show-login], [data-show-register], [data-show-forgot]');
    if (viewButton) {
      const name = viewButton.hasAttribute('data-show-login')
        ? 'login'
        : viewButton.hasAttribute('data-show-register') ? 'register' : 'forgot';
      showView(name);
    }
  });

  root.querySelector('[data-account-logout]').addEventListener('click', async () => {
    let token;
    try { token = sessionStorage.getItem(tokenKey); } catch (error) {
      feedback.textContent = 'Não foi possível acessar a sessão da conta neste navegador.';
      return;
    }
    try {
      if (token) await request('/api/account/logout', {}, token);
    } catch (error) {
      console.error('Não foi possível encerrar a sessão na API.', error);
    } finally {
      try { sessionStorage.removeItem(tokenKey); } catch (error) {
        feedback.textContent = 'Não foi possível remover a sessão salva neste navegador.';
      }
      showView('login');
      feedback.textContent = 'Você saiu da sua conta.';
    }
  });

  showView('login');
  try {
    const token = sessionStorage.getItem(tokenKey);
    if (!token) return;
    fetch(`${apiBase}/api/account/me`, { headers: { Authorization: `Bearer ${token}` } })
      .then(async response => {
        const account = await response.json().catch(() => ({}));
        if (!response.ok) {
          if (response.status === 401) sessionStorage.removeItem(tokenKey);
          throw new Error(account.message || 'Sua sessão expirou. Entre novamente.');
        }
        await renderDashboard(account);
      })
      .catch(error => { feedback.textContent = error.message; });
  } catch (error) {
    feedback.textContent = 'Não foi possível acessar a sessão da conta neste navegador.';
  }
})();
