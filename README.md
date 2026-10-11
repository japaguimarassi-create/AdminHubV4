# J.A.R.V.I.S. 8.0.0

Aplicativo Android com tela de login inicial, interface de conversa, histórico local, esfera de voz e integração como assistente do sistema. A lógica local usa classificação determinística e comandos Android; consultas Web são explícitas e executadas numa WebView interna.

## Reconstrução desta versão

- A primeira tela agora é o login. O botão Google usa Android Credential Manager e envia o token de identidade ao endpoint HTTPS configurado para validação real.
- O backend Supabase de referência está em supabase/functions/jarvis-auth/index.ts. A migração supabase/migrations/20261011000000_jarvis_global_owner.sql define o primeiro proprietário com uma operação atômica no banco; o papel nunca é eleito por preferência local do aplicativo.
- A UI principal tem conversas locais, histórico lateral e campo de mensagens estilo chat.
- A invocação do assistente Android usa uma esfera azul/ciano/violeta na parte inferior. A animação pulsa uniformemente.
- A transcrição da voz passa por classificação e confirmação antes da execução. Comandos ambíguos não são encaminhados automaticamente à Web nem disparam ações.
- As conversas permanecem no dispositivo. Pesquisa Web abre dentro do aplicativo.
- A acessibilidade permanece opcional e deve ser habilitada manualmente nas configurações do Android.

## Configurar login Google real

Este repositório ainda não tem projeto Supabase provisionado nem cliente OAuth configurado. Sem esses recursos, o botão Google informa a pendência em vez de simular autenticação.

Para habilitar a autenticação global:

1. Configure um projeto Supabase e aplique o SQL da pasta supabase/migrations.
2. Configure os segredos da função Edge: GOOGLE_WEB_CLIENT_ID, SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY. Nunca compile a service role key no APK.
3. Publique supabase/functions/jarvis-auth.
4. Crie um OAuth Client ID da Web no Google Auth Platform e configure o cliente Android com package name e fingerprint do certificado de assinatura que será usado.
5. Nas variáveis do GitHub Actions, defina JARVIS_GOOGLE_WEB_CLIENT_ID e JARVIS_AUTH_ENDPOINT (URL HTTPS de /functions/v1/jarvis-auth) antes de gerar o APK final.
6. Use assinatura estável para builds que precisem funcionar com o cliente Android do Google. Um APK debug assinado por uma chave efêmera do runner não é adequado para uma configuração OAuth Android permanente.

A função valida assinatura, emissor, audiência, expiração e nonce do token Google no servidor. A regra do primeiro proprietário é garantida por uma linha singleton e inserção atômica no PostgreSQL. O modo local não cria proprietário global.

## Comandos e confiabilidade

A voz offline depende de microfone permitido e do modelo Português (Brasil) instalado no reconhecedor do Android. Quando a transcrição tem baixa confiança, o app pede repetição. Caso o comando reconhecido seja válido, ele fica pendente até confirmação por voz ("confirmar") ou pelo botão. "Cancelar" descarta-o.

O classificador local e o parser não são um LLM generativo. Eles reconhecem um conjunto de comandos e perguntas; frases fora desse conjunto recebem solicitação de reformulação em vez de executar uma ação aleatória. A pesquisa Web é explícita.

A automação entre aplicativos exige ativação manual de Acessibilidade e só pode operar controles que o Android exponha. Jogos baseados em canvas podem não expor elementos clicáveis. O projeto não injeta código em jogos nem garante ações automáticas universais.

## Compilar

O GitHub Actions constrói o APK com pacote com.jarvis.assistant.debug8, executa testes unitários e valida a assinatura. O APK de depuração usa assinatura de teste do CI, não uma chave de distribuição permanente.
