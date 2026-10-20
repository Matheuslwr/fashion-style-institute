(() => {
  const page = document.querySelector('main:not(.admin-store)');
  if (!page || document.querySelector('.admin-store')) return;

  const siteRoot = new URL(window.location.pathname.includes('/pages/') ? '../' : './', window.location.href);
  const destinations = {
    home: new URL('index.html', siteRoot).href,
    shop: new URL('pages/design-de-moda-com-proposito.html', siteRoot).href,
    cart: new URL('pages/carrinho.html', siteRoot).href,
    orders: new URL('pages/acompanhar-pedido.html', siteRoot).href,
    search: new URL('pages/design-de-moda-com-proposito.html?buscar=1', siteRoot).href,
    account: new URL('pages/conta.html', siteRoot).href
  };
  const actions = document.querySelector('.store-header .store-actions');
  if (actions && !actions.querySelector('[data-account-link]')) {
    const accountLink = document.createElement('a');
    accountLink.className = 'tracking-nav-button';
    accountLink.href = destinations.account;
    accountLink.dataset.accountLink = '';
    accountLink.setAttribute('aria-label', 'Minha conta');
    accountLink.innerHTML = '<span aria-hidden="true">♙</span><span>Minha conta</span>';
    actions.append(accountLink);
  }
  const pathname = window.location.pathname;
  const activeItem = /\/index\.html$|\/$/.test(pathname)
    ? 'home'
    : /\/(carrinho|checkout)\.html$/.test(pathname)
      ? 'cart'
      : /\/(acompanhar-pedido|pedido-confirmado)\.html$/.test(pathname)
        ? 'orders'
        : 'shop';

  const nav = document.createElement('nav');
  nav.className = 'mobile-bottom-nav';
  nav.setAttribute('aria-label', 'Navegação móvel');
  nav.innerHTML = `
    <a class="mobile-bottom-nav-item" href="${destinations.home}" data-nav-item="home" aria-label="Início">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/></svg><span>Início</span>
    </a>
    <a class="mobile-bottom-nav-item" href="${destinations.shop}" data-nav-item="shop" aria-label="Loja">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8h16l-1 13H5L4 8Z"/><path d="M9 9V6a3 3 0 0 1 6 0v3"/></svg><span>Loja</span>
    </a>
    <a class="mobile-bottom-nav-item" href="${destinations.cart}" data-nav-item="cart" aria-label="Carrinho">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.2 11.1A2 2 0 0 0 9.2 17h8.9a2 2 0 0 0 2-1.6L22 8H6"/><circle cx="10" cy="21" r="1"/><circle cx="19" cy="21" r="1"/></svg><span>Carrinho</span><small data-mobile-cart-count aria-label="0 itens no carrinho">0</small>
    </a>
    <a class="mobile-bottom-nav-item" href="${destinations.orders}" data-nav-item="orders" aria-label="Pedidos">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12a2 2 0 0 1 2 2v16H4V5a2 2 0 0 1 2-2Z"/><path d="M8 8h8M8 12h8M8 16h5"/></svg><span>Pedidos</span>
    </a>
    <button class="mobile-bottom-nav-item" type="button" data-mobile-search aria-label="Procurar produtos">
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="7.3"/><path d="m16.2 16.2 4.3 4.3"/></svg><span>Procurar</span>
    </button>
  `;
  nav.querySelector(`[data-nav-item="${activeItem}"]`)?.setAttribute('aria-current', 'page');
  document.body.append(nav);

  const updateCartCount = () => {
    const counter = nav.querySelector('[data-mobile-cart-count]');
    try {
      const cart = JSON.parse(localStorage.getItem('fsi-demo-cart-v1') || '[]');
      const count = Array.isArray(cart)
        ? cart.reduce((total, item) => total + (Number.isInteger(item.quantity) && item.quantity > 0 ? item.quantity : 0), 0)
        : 0;
      counter.textContent = String(count);
      counter.setAttribute('aria-label', `${count} ${count === 1 ? 'item' : 'itens'} no carrinho`);
      counter.hidden = count === 0;
    } catch (error) {
      counter.hidden = true;
      console.error('Não foi possível ler a quantidade de itens do carrinho.', error);
    }
  };

  updateCartCount();
  window.addEventListener('storage', updateCartCount);
  window.addEventListener('fsi-cart-updated', updateCartCount);

  nav.querySelector('[data-mobile-search]').addEventListener('click', () => {
    const toggle = document.querySelector('.store-header [data-search-toggle]');
    if (toggle) {
      toggle.click();
      return;
    }
    window.location.href = destinations.search;
  });

  let previousScrollY = window.scrollY;
  let scrollFrame = null;
  window.addEventListener('scroll', () => {
    if (scrollFrame !== null) return;
    scrollFrame = window.requestAnimationFrame(() => {
      const currentScrollY = window.scrollY;
      const scrollingDown = currentScrollY > previousScrollY;
      const hasRoomToScroll = document.documentElement.scrollHeight > window.innerHeight + 100;
      nav.classList.toggle('is-scroll-hidden', hasRoomToScroll && currentScrollY > 90 && scrollingDown);
      previousScrollY = currentScrollY;
      scrollFrame = null;
    });
  }, { passive: true });
})();
