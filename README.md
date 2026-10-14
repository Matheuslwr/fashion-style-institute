# Fashion Style Institute

Site estático publicado no GitHub Pages e API de catálogo em Java/Spring Boot com PostgreSQL. O banco é a fonte de produtos, categorias, preços, imagens, variantes e estoque. O carrinho continua no `localStorage`; checkout, frete, confirmação e acompanhamento permanecem demonstrativos, sem pagamento ou pedidos reais.

## Requisitos locais

- JDK 21
- Maven 3.9 ou superior
- VS Code Live Server (ou outro servidor HTTP estático)
- Docker Desktop com Docker Compose **somente se** quiser PostgreSQL local. Se não tiver permissão para instalar Docker, use um PostgreSQL remoto como Neon.

## Executar localmente no Windows

1. Escolha onde ficará o PostgreSQL:

   - **Local com Docker:** instale Docker Desktop e execute na raiz do projeto:

   ```powershell
   docker compose up -d postgres
   ```

   - **Remoto com Neon:** não precisa iniciar Docker nem instalar PostgreSQL no Windows. Configure `DATABASE_URL` no formato JDBC, `DATABASE_USERNAME` e `DATABASE_PASSWORD` no PowerShell antes de iniciar a API. Use um projeto de desenvolvimento separado do banco de produção.

2. Abra um terminal PowerShell e configure as credenciais do administrador. A senha é obrigatória e não existe senha padrão na aplicação:

   ```powershell
   $env:ADMIN_USERNAME = "admin"
   $env:ADMIN_PASSWORD = "escolha-uma-senha-forte"
   ```

3. Na raiz do projeto, inicie a API. O Flyway cria as tabelas e carrega os seis vestidos que já existiam na loja:

   ```powershell
   mvn -f backend/pom.xml spring-boot:run
   ```

   A API ficará disponível em `http://localhost:8080`. Com Docker, o PostgreSQL local usa `fsi_store` / `fsi_store_local`, já alinhados ao `docker-compose.yml`. Para evitar que o Live Server recarregue o painel quando um upload criar arquivo, salve uploads locais fora da pasta do site:

   ```powershell
   $env:UPLOAD_DIR = "$env:USERPROFILE\fsi-store-uploads"
   ```

   Em desenvolvimento, `IMAGE_STORAGE` usa `local` por padrão. Na publicação gratuita, o Render configura `IMAGE_STORAGE=cloudinary`.

4. No VS Code, abra `pages/design-de-moda-com-proposito.html` com Live Server e use a porta `5500`. O endereço do backend local já está configurado em `js/api-config.js` como `http://localhost:8080`. A API permite por padrão as origens `localhost:5500`, `127.0.0.1:5500` e `localhost:8080`.

5. Acesse `pages/admin-produtos.html`, entre com as credenciais definidas no passo 2 e cadastre produtos. O painel permite incluir imagens e variantes e definir estoque, preço promocional, categoria, destaque e status ativo. Categorias novas ganham uma página de listagem genérica automaticamente.

Para interromper o banco local:

```powershell
docker compose down
```

Os dados permanecem no volume nomeado. Para apagá-los, remova esse volume explicitamente com `docker compose down -v`.

## API

| Método | Rota | Acesso | Uso |
| --- | --- | --- | --- |
| `GET` | `/api/products` | Público | Lista produtos ativos; aceita `category`, `featured` e `q` |
| `GET` | `/api/products/{slug}` | Público | Detalhe de um produto ativo |
| `GET` | `/api/categories` | Público | Categorias cadastradas |
| `GET` | `/api/admin/products` | Administrador | Lista produtos ativos e inativos |
| `POST` | `/api/admin/products` | Administrador | Cadastra produto |
| `PUT` | `/api/admin/products/{id}` | Administrador | Atualiza produto |
| `DELETE` | `/api/admin/products/{id}` | Administrador | Exclui produto |
| `POST` | `/api/admin/categories` | Administrador | Cadastra categoria |
| `POST` | `/api/admin/images` | Administrador | Envia imagem multipart, até 8 MB |

As rotas administrativas usam HTTP Basic. O navegador envia a credencial somente durante a sessão do painel; ela não é gravada em `localStorage` ou `sessionStorage`. O backend valida os dados e o banco protege SKUs e combinações repetidas de cor/tamanho.

## Publicação gratuita

O repositório inclui `backend/Dockerfile` e `render.yaml` para publicar a API no Render. A configuração gratuita usa o Neon já criado para PostgreSQL e o Cloudinary para que imagens sobrevivam a reinícios. O site estático pode ser publicado no GitHub Pages ou na Vercel.

**Limites importantes do plano gratuito:** o Render suspende o serviço após 15 minutos sem tráfego e o primeiro acesso pode levar cerca de um minuto para acordá-lo. O sistema de arquivos do Render Free é temporário, portanto o backend usa Cloudinary em produção. O Neon também suspende o compute quando fica inativo. Esses planos são adequados para teste e projetos pessoais, mas não oferecem disponibilidade contínua nem SLA. Confira os limites atuais dos provedores antes de publicar para uso comercial.

### Preparação

1. Envie as alterações do projeto para um repositório GitHub que você controla. Não inclua senhas, `CLOUDINARY_URL` nem a URL completa do banco com senha.
2. Crie uma conta gratuita no [Cloudinary](https://cloudinary.com/users/register_free). No painel, abra **Settings → API Keys** e copie o valor **API Environment variable**. Ele contém credenciais secretas; guarde-o para colar diretamente no Render, nunca no Git ou nesta conversa.
3. Tenha em mãos os valores de conexão JDBC do projeto Neon: URL `jdbc:postgresql://...?...sslmode=require`, usuário e senha. Se o Neon fornecer `postgresql://`, adapte para JDBC removendo usuário/senha do URL e fornecendo-os nas variáveis separadas. Use um banco separado dos dados de produção real enquanto estiver testando.

### API no Render

1. Crie uma conta no [Render](https://render.com/) e conecte o GitHub.
2. No painel Render, escolha **New → Blueprint** e selecione o repositório com este `render.yaml`.
3. Revise a configuração antes de criar o serviço. O Blueprint cria `fsi-store-api` como web service Docker gratuito na região Virginia; não cria outro PostgreSQL no Render.
4. Quando solicitado, preencha as variáveis secretas diretamente no painel:
   - `DATABASE_URL`: URL JDBC completa do Neon, com `sslmode=require` e sem senha embutida.
   - `DATABASE_USERNAME` e `DATABASE_PASSWORD`: usuário e senha do Neon.
   - `ADMIN_PASSWORD`: senha forte exclusiva para o painel.
   - `CORS_ALLOWED_ORIGINS` já está definido no Blueprint como `https://matheuslwr.github.io`, a origem do GitHub Pages atual. Se trocar para um domínio próprio, altere esse valor para a nova origem HTTPS, sem caminho. Não use `*`.
   - `CLOUDINARY_URL`: valor **API Environment variable** do Cloudinary.
   - `ADMIN_USERNAME` já está definido como `admin`; altere depois pelo ambiente se desejar outro nome.
5. Aguarde o deploy. O endpoint de saúde configurado é `/api/categories`. Teste `https://<nome-do-servico>.onrender.com/api/categories` e `/api/products` no navegador. O Render pode levar aproximadamente um minuto para responder após inatividade.

O `render.yaml` marca segredos como `sync: false`; eles são pedidos no primeiro provisionamento. Se o serviço já foi criado, cadastre/atualize os segredos em **Render → serviço → Environment**. Nunca coloque seus valores em `render.yaml`.

### GitHub Pages apontando para a API

1. Depois que a API estiver ativa, abra `js/api-config.js` e troque `http://localhost:8080` pela URL HTTPS do serviço Render, por exemplo `https://fsi-store-api.onrender.com`.
2. Confirme que `CORS_ALLOWED_ORIGINS` no Render contém `https://matheuslwr.github.io`. Para GitHub Pages de projeto, CORS usa somente a origem `https://usuario.github.io`, sem `/nome-do-repositorio`.
3. Envie essa alteração ao GitHub e publique o site. A API pode acordar lentamente após inatividade; isso não significa que o catálogo perdeu dados.
4. Abra a página administrativa publicada em `https://seu-usuario.github.io/<repositorio>/pages/admin-produtos.html`. HTTP Basic protege os endpoints de escrita; cadastros e atualizações públicos só funcionam com a senha administrativa configurada no Render.

Os produtos e categorias ficam no PostgreSQL; imagens novas ficam no Cloudinary e suas URLs HTTPS são gravadas no PostgreSQL. As imagens iniciais, já versionadas em `img/Produtos/`, continuam sendo servidas pelo GitHub Pages. Os arquivos enviados durante o teste local ficam na pasta local indicada por `UPLOAD_DIR` e não são copiados automaticamente ao Cloudinary.

### Site estático na Vercel

O `vercel.json` publica apenas os arquivos do site (`index.html`, `css/`, `img/`, `js/` e `pages/`), sem tentar executar a API Java no serviço da Vercel. Para usar:

1. Envie `vercel.json` e as demais alterações ao GitHub.
2. Na Vercel, escolha **Add New → Project**, importe este repositório e mantenha a pasta raiz como diretório do projeto.
3. A configuração do repositório define o comando de build e a pasta publicada. A cada push na branch `main`, a Vercel publica uma nova versão e fornece um endereço gratuito `*.vercel.app`.

A API continua no Render; o endereço está em `js/api-config.js`. Portanto, não é necessário alterar o Neon, o Cloudinary ou as variáveis secretas do Render para hospedar o site na Vercel. O GitHub continua sendo o repositório do código, mas não aparece no endereço `vercel.app`.

Para sair do plano gratuito futuramente, use um serviço sempre ativo e armazenamento de imagens persistente. Não remova a configuração Cloudinary sem mover os arquivos existentes e atualizar os URLs das imagens.

## Estrutura

- `backend/`: API Spring Boot, entidades JPA, validação, segurança, armazenamento local/Cloudinary e migrações Flyway.
- `backend/Dockerfile` e `render.yaml`: imagem Java 21 e Blueprint para o serviço gratuito no Render.
- `backend/src/main/resources/db/migration/`: esquema do catálogo e carga inicial de produtos/categorias.
- `pages/admin-produtos.html`, `js/admin-produtos.js`, `css/admin-produtos.css`: painel administrativo.
- `js/api-config.js`: URL base da API por ambiente.
- `js/store.js`: renderização dinâmica do catálogo; carrinho e checkout demonstrativo continuam no navegador.

Para executar os testes do backend:

```powershell
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "test-password"
mvn -f backend/pom.xml test
```