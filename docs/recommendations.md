# Recomendações e catálogos

O GSZ TV combina as recomendações do TMDB com os catálogos de addons configurados pelo usuário. A quantidade definida em **Destaques por aba** é o total navegável em cada aba; os demais cartões são alcançados com as setas laterais.

## Frequência e desempenho

- A lista do TMDB e os catálogos dos addons ficam em cache por **6 horas**. A atualização ocorre quando a tela inicial está ativa e usa um modo com destaques.
- A seleção de cartões alterna **a cada hora** usando os dados já salvos. Essa alternância não exige buscar novamente o catálogo inteiro.
- Mudar de manhã para tarde ou noite não força uma nova busca. Alterações na chave TMDB, idioma ou fontes continuam sendo consideradas.
- A lista anterior continua disponível quando a conexão falha. Novas tentativas são espaçadas em 1, 2, 4, 8 e depois até 15 minutos. O retorno da rede pode antecipar a tentativa do TMDB.
- O launcher evita trocar a seleção durante a navegação nos cartões. As mudanças ficam disponíveis quando o usuário deixa essa faixa.
- Sem streaming reconhecido instalado, o TMDB carrega primeiro as duas listas de tendências. Detalhes e imagens são carregados em segundo plano para os títulos selecionados.

As tendências do TMDB são gerais, combinadas com disponibilidade nos serviços instalados quando possível. Elas não representam recomendações personalizadas pelo histórico do Nuvio. Se Trakt estiver configurado, sua integração continua sendo usada nos serviços compatíveis.

## Catálogos usados no Nuvio oficial

O Nuvio oficial compartilha histórico com o Android TV, mas não exporta para outros launchers a configuração privada de sua página inicial. Para usar a mesma fonte de catálogo no GSZ TV:

1. Copie o link de instalação do addon de **catálogo** que você utiliza no Nuvio.
2. No GSZ TV, abra **Home Settings → Picks → Catálogos do Nuvio**.
3. Informe o link completo terminado em `/manifest.json`. É possível usar até quatro links, um por linha.
4. Salve. O GSZ TV combina os títulos dos addons com os do TMDB e remove duplicatas de nome e tipo.

O cartão identifica o Nuvio como origem. Selecioná-lo abre os detalhes no Nuvio pelo link oficial `nuvio://detail/...`. O Nuvio deve estar instalado. Se sua versão não aceitar o link, o launcher tenta abrir o aplicativo.

Somente os recursos de metadados `manifest` e `catalog` são consultados; o launcher não busca streams. Addons que oferecem apenas reprodução, ou catálogos que exigem busca/filtros obrigatórios, não fornecem títulos para essa faixa. O resultado também depende dos dados publicados pelo addon; não é uma cópia automática da ordem e de todas as preferências privadas da página inicial do Nuvio.

Os links ficam nas configurações privadas do GSZ TV. Configure links personalizados no aparelho, sem publicá-los em commits, capturas de tela ou relatórios.

Para manter o launcher leve, o carregamento tem limites de seis catálogos (até três de filmes e três de séries), 48 títulos no total, tamanho de resposta e tempo de conexão. O histórico em **Continuar assistindo** continua vindo das listas compartilhadas do Android TV.

## Verificação

Depois de compilar o APK, execute no PowerShell, apontando para um emulador Android:

```powershell
.\tools\check-recommendations.ps1 -Serial emulator-5554
```

As verificações rodam as classes reais no Android com dados fictícios, sem chave TMDB e sem acessar contas: cache de seis horas, rotação horária, recuo de tentativas, validação de links de addons, classificação de filmes/séries, limites, duplicatas, links do Nuvio, respostas HTTP e redirecionamentos.

Referências de interoperabilidade: [Nuvio DeepLinkParser](https://github.com/NuvioMedia/NuvioTV/blob/main/app/src/main/java/com/nuvio/tv/core/deeplink/DeepLinkParser.kt), [Nuvio AndroidTvChannelManager](https://github.com/NuvioMedia/NuvioTV/blob/main/app/src/main/java/com/nuvio/tv/core/sync/androidtv/AndroidTvChannelManager.kt), [formato de manifesto](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md) e [resposta de catálogo](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineCatalogHandler.md).
