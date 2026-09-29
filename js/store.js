(() => {
  const product = {
    id: 'vestido-liliany',
    name: 'Vestido LILIANY',
    price: 22990,
    images: {
      'Azul-marinho': 'Vestido1-Azul-Marinho.webp',
      'Azul serenity': 'Vestido1-Azul-Serenity.webp',
      Verde: 'Vestido1-Verde.webp',
      Roxo: 'Vestido1-Roxo.webp',
      Vermelho: 'Vestido1-Vermelho.webp'
    }
  };
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
  const formatMoney = (cents) => new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL'
  }).format(cents / 100);

  function readCart() {
    try {
      const savedCart = JSON.parse(localStorage.getItem(cartKey) || '[]');
      if (!Array.isArray(savedCart)) return [];
      return savedCart.filter((item) => {
        const colorSizes = item.color === 'Verde' || item.color === 'Roxo'
          ? ['38', '40', '41']
          : availableSizes;
        return item.productId === product.id
          && Object.hasOwn(product.images, item.color)
          && colorSizes.includes(String(item.size))
          && Number.isInteger(item.quantity)
          && item.quantity > 0;
      }).map((item) => ({
        productId: product.id,
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
      showFeedback('Não foi possível salvar a sacola neste navegador.');
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
      if (link) link.setAttribute('aria-label', `Sacola com ${count} ${count === 1 ? 'item' : 'itens'}`);
    });
  }

  function showFeedback(message) {
    const feedback = document.querySelector('[data-store-feedback]');
    if (feedback) feedback.textContent = message;
  }

  function makeCartRow(item, checkout = false) {
    const row = document.createElement('article');
    row.className = checkout ? 'checkout-item' : 'cart-item';

    const image = document.createElement('img');
    image.src = `../img/Produtos/${product.images[item.color]}`;
    image.alt = `${product.name}, cor ${item.color}`;
    image.loading = 'lazy';
    row.append(image);

    const details = document.createElement('div');
    details.className = checkout ? 'checkout-item-details' : 'cart-item-details';
    const name = document.createElement('h2');
    name.textContent = product.name;
    const variant = document.createElement('p');
    variant.textContent = `Cor: ${item.color} · Tamanho: ${item.size}`;
    const unitPrice = document.createElement('p');
    unitPrice.className = 'cart-item-price';
    unitPrice.textContent = `${formatMoney(product.price)} cada`;
    details.append(name, variant, unitPrice);
    row.append(details);

    if (checkout) {
      const quantity = document.createElement('span');
      quantity.className = 'checkout-item-quantity';
      quantity.textContent = `${item.quantity} × ${formatMoney(product.price)}`;
      row.append(quantity);
      return row;
    }

    const key = `${item.color}|${item.size}`;
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
    decrease.setAttribute('aria-label', `Diminuir quantidade de ${product.name}, ${item.color}, tamanho ${item.size}`);
    decrease.textContent = '−';
    const quantity = document.createElement('output');
    quantity.textContent = String(item.quantity);
    const increase = document.createElement('button');
    increase.type = 'button';
    increase.dataset.cartAction = 'increase';
    increase.dataset.cartKey = key;
    increase.setAttribute('aria-label', `Aumentar quantidade de ${product.name}, ${item.color}, tamanho ${item.size}`);
    increase.textContent = '+';
    quantityControl.append(decrease, quantity, increase);
    const lineTotal = document.createElement('strong');
    lineTotal.className = 'cart-line-total';
    lineTotal.textContent = formatMoney(product.price * item.quantity);
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
    return cart.reduce((total, item) => total + product.price * item.quantity, 0);
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
    if (!Object.hasOwn(product.images, color) || !availableSizes.includes(size)) {
      showFeedback('Selecione uma cor e um tamanho disponíveis.');
      return;
    }
    const validSizes = color === 'Verde' || color === 'Roxo' ? ['38', '40', '41'] : availableSizes;
    if (!validSizes.includes(size)) {
      showFeedback('Esse tamanho não está disponível para a cor selecionada.');
      return;
    }
    const cart = readCart();
    const existingItem = cart.find((item) => item.color === color && item.size === size);
    if (existingItem) existingItem.quantity = Math.min(existingItem.quantity + 1, 99);
    else cart.push({ productId: product.id, color, size, quantity: 1 });
    writeCart(cart);
    showFeedback(`${product.name} adicionado à sacola.`);
  });

  document.querySelector('[data-cart-items]')?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-cart-action]');
    if (!button) return;
    const [color, size] = button.dataset.cartKey.split('|');
    const cart = readCart();
    const item = cart.find((entry) => entry.color === color && entry.size === size);
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
