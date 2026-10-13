INSERT INTO categories (name, slug) VALUES
    ('Vestir', 'vestir'),
    ('Acessórios', 'acessorios'),
    ('Calçados e variedades', 'calcados-e-variedades');

INSERT INTO products (name, slug, description, price_cents, promotional_price_cents, featured, active, category_id)
SELECT seed.name, seed.slug, seed.description, seed.price_cents, NULL, seed.featured, TRUE, category.id
FROM (VALUES
    ('Vestido LILIANY', 'vestido-liliany', 'Tule · modelagem elegante', 22990, TRUE),
    ('Vestido AMÉLIA', 'vestido-amelia', 'Caimento fluido · cores intensas', 24990, TRUE),
    ('Vestido LAURA', 'vestido-laura', 'Silhueta marcante · toque leve', 21990, TRUE),
    ('Vestido LIRA', 'vestido-lira', 'Design contemporâneo · várias cores', 23990, FALSE),
    ('Vestido NANDA', 'vestido-nanda', 'Conforto e cor para o dia a dia', 19990, FALSE),
    ('Vestido ZAZA', 'vestido-zaza', 'Leveza · paleta vibrante', 18990, FALSE)
) AS seed(name, slug, description, price_cents, featured)
JOIN categories category ON category.slug = 'vestir';

INSERT INTO product_images (product_id, image_url, alt_text, color, display_order)
SELECT product.id,
       'img/Produtos/' || seed.filename,
    product.name || ' na cor ' || seed.color,
       seed.color,
       row_number() OVER (PARTITION BY product.id ORDER BY seed.filename) - 1
FROM (VALUES
    ('vestido-liliany', 'Vestido1-Azul-Marinho.webp', 'Azul-marinho'),
    ('vestido-liliany', 'Vestido1-Azul-Serenity.webp', 'Azul serenity'),
    ('vestido-liliany', 'Vestido1-Laranja.webp', 'Laranja'),
    ('vestido-liliany', 'Vestido1-Rosé.webp', 'Rosé'),
    ('vestido-liliany', 'Vestido1-Roxo.webp', 'Roxo'),
    ('vestido-liliany', 'Vestido1-Verde.webp', 'Verde'),
    ('vestido-liliany', 'Vestido1-Vermelho.webp', 'Vermelho'),
    ('vestido-amelia', 'VestidoAmeliaAzulMarinho.webp', 'Azul-marinho'),
    ('vestido-amelia', 'VestidoAmeliaMarsala.webp', 'Marsala'),
    ('vestido-amelia', 'VestidoAmeliaVerdeOliva.png', 'Verde oliva'),
    ('vestido-laura', 'VestidoLauraAzulSerenity.webp', 'Azul serenity'),
    ('vestido-laura', 'VestidoLauraFúcsia.webp', 'Fúcsia'),
    ('vestido-laura', 'VestidoLauraMarsala.webp', 'Marsala'),
    ('vestido-laura', 'VestidoLauraVerdeOliva.webp', 'Verde oliva'),
    ('vestido-lira', 'VestidoLiraAzulBic.webp', 'Azul bic'),
    ('vestido-lira', 'VestidoLiraFúcsia.webp', 'Fúcsia'),
    ('vestido-lira', 'VestidoLiraMarrom.webp', 'Marrom'),
    ('vestido-lira', 'VestidoLiraUva.webp', 'Uva'),
    ('vestido-lira', 'VestidoLiraVinho.webp', 'Vinho'),
    ('vestido-nanda', 'VestidoNandaAzulMarinho.webp', 'Azul-marinho'),
    ('vestido-nanda', 'VestidoNandaAzulSerenity.webp', 'Azul serenity'),
    ('vestido-zaza', 'VestidoZazaAmarelo.webp', 'Amarelo'),
    ('vestido-zaza', 'VestidoZazaFúcsia.webp', 'Fúcsia'),
    ('vestido-zaza', 'VestidoZazaVerdeMenta.webp', 'Verde menta'),
    ('vestido-zaza', 'VestidoZazaVerdeOliva.webp', 'Verde oliva')
) AS seed(product_slug, filename, color)
JOIN products product ON product.slug = seed.product_slug;

INSERT INTO product_variants (product_id, sku, color, size, stock)
SELECT product.id,
    upper(replace(product.slug, '-', '')) || '-' || row_number() OVER (PARTITION BY product.slug ORDER BY image.color, size.value),
       image.color, size.value, 3
FROM products product
JOIN product_images image ON image.product_id = product.id
CROSS JOIN (VALUES ('38'), ('39'), ('40'), ('41'), ('42')) AS size(value)
WHERE product.slug IN ('vestido-liliany', 'vestido-amelia', 'vestido-laura', 'vestido-lira', 'vestido-nanda', 'vestido-zaza');

UPDATE product_variants variant SET stock = 0
FROM products product
WHERE variant.product_id = product.id
  AND ((product.slug = 'vestido-liliany' AND ((variant.color = 'Rosé') OR (variant.color = 'Azul-marinho' AND variant.size = '39') OR (variant.color = 'Azul serenity' AND variant.size = '41') OR (variant.color = 'Laranja' AND variant.size IN ('40', '41')) OR (variant.color = 'Roxo' AND variant.size IN ('39', '42')) OR (variant.color = 'Verde' AND variant.size = '42') OR (variant.color = 'Vermelho' AND variant.size = '40')))
    OR (product.slug = 'vestido-amelia' AND ((variant.color = 'Marsala') OR (variant.color = 'Azul-marinho' AND variant.size = '41') OR (variant.color = 'Verde oliva' AND variant.size IN ('39', '42'))))
    OR (product.slug = 'vestido-laura' AND ((variant.color = 'Azul serenity' AND variant.size = '40') OR (variant.color = 'Fúcsia' AND variant.size IN ('39', '41')) OR (variant.color = 'Marsala' AND variant.size = '42') OR (variant.color = 'Verde oliva' AND variant.size = '38')))
    OR (product.slug = 'vestido-lira' AND ((variant.color = 'Uva') OR (variant.color = 'Azul bic' AND variant.size IN ('39', '42')) OR (variant.color = 'Fúcsia' AND variant.size = '41') OR (variant.color = 'Marrom' AND variant.size = '40') OR (variant.color = 'Vinho' AND variant.size IN ('39', '41'))))
    OR (product.slug = 'vestido-nanda' AND ((variant.color = 'Azul-marinho' AND variant.size IN ('40', '42')) OR (variant.color = 'Azul serenity' AND variant.size IN ('38', '41'))))
    OR (product.slug = 'vestido-zaza' AND ((variant.color = 'Amarelo' AND variant.size IN ('39', '41')) OR (variant.color = 'Fúcsia' AND variant.size = '40') OR (variant.color = 'Verde menta' AND variant.size IN ('38', '42')) OR (variant.color = 'Verde oliva' AND variant.size IN ('39', '40')))));