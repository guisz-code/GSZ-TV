# GSZ TV

<p align="center">
  <img src="app/res/drawable-nodpi/icon.png" alt="Ícone do GSZ TV" width="180">
</p>

<p align="center">
  <strong>Launcher leve, simples e sem anúncios para Android TV.</strong>
</p>

O GSZ TV oferece uma tela inicial mais limpa e pensada para navegação com controle remoto. O projeto foi desenvolvido e testado principalmente no projetor **Magcubic HY300 com Android 12**.

> [!WARNING]
> O GSZ TV pode assumir a tela inicial do dispositivo em modelos compatíveis. Antes de ativá-lo, leia a seção [Depois da instalação](#depois-da-instalação). Caso necessário, é possível voltar para a tela inicial original nas configurações do aplicativo.

## Baixar e instalar

1. Abra a página de [Releases](https://github.com/guisz-code/GSZ-TV/releases/latest) e baixe o arquivo **`GSZTV.apk`**.
2. Envie o arquivo para a TV ou projetor usando um pendrive, gerenciador de arquivos, nuvem ou aplicativo de transferência de arquivos.
3. Na TV, abra o arquivo `GSZTV.apk`.
4. Quando o Android solicitar, permita a instalação de aplicativos desconhecidos para o aplicativo que está abrindo o arquivo (por exemplo, o gerenciador de arquivos).
5. Confirme a instalação e abra o **GSZ TV**.

Se aparecer um aviso de segurança do Android, isso é esperado para aplicativos instalados fora da Play Store. Baixe o APK somente pela página oficial de Releases deste repositório.

### Atualizar uma instalação existente

Baixe a versão mais recente e instale-a por cima da versão atual. Não desinstale o GSZ TV antes de atualizar: assim suas configurações são preservadas.

## Depois da instalação

Ao abrir o GSZ TV, acesse **Início** nas configurações do aplicativo para escolher entre:

- **GSZ TV**: usa o GSZ TV como tela inicial em aparelhos compatíveis.
- **Original**: restaura a tela inicial e o protetor de tela de fábrica.

Em alguns modelos, como Fire TV ou aparelhos onde a troca da tela inicial não é segura, o GSZ TV continua disponível como um aplicativo comum. Nesses casos, abra-o pela lista de aplicativos.

## Compatibilidade

- Android 8.0 (API 26) ou superior.
- Android TV e dispositivos compatíveis com navegação por controle remoto.
- Dispositivo de referência: Magcubic HY300 com Android 12.

A compatibilidade pode variar conforme o fabricante e a versão do Android. O aplicativo não é indicado para celulares ou tablets.

## Recursos

- Interface sem anúncios e otimizada para TV.
- Navegação por controle remoto.
- Lista de aplicativos e atalhos para recursos do dispositivo.
- Opções de personalização pela tela de configurações.
- Verificação de atualizações pela página de Releases.

## Instalação pelo computador (ADB)

Esta opção é útil para quem prefere instalar pela rede ou por USB com a depuração ADB ativada:

```bash
adb install -r GSZTV.apk
```

Para conectar via Wi-Fi:

```bash
adb connect IP_DA_TV:PORTA
adb install -r GSZTV.apk
```

## Compilar o projeto

Para criar um APK genérico de distribuição, instale o **JDK 17 ou superior**, o **Android SDK Platform** e o **Android Build Tools 34 ou superior**. Configure `JAVA_HOME` e `ANDROID_HOME` (ou `ANDROID_SDK_ROOT`) e execute:

```bash
git clone https://github.com/guisz-code/GSZ-TV.git
cd GSZ-TV
bash build.sh --generic
```

O arquivo gerado será `GSZTV.apk` na raiz do projeto. O modo `--generic` não inclui chaves ou dados pessoais no APK e é o formato recomendado para Releases públicas.

## Estrutura do projeto

```text
GSZ-TV/
├── app/              # Código-fonte e recursos Android
├── build.sh          # Script de compilação sem Gradle
├── LICENSE
└── README.md
```

## Créditos e licença

O GSZ TV tem como referência o projeto Free My TV. Os avisos e licenças de componentes de terceiros devem ser preservados.

Este projeto é distribuído sob a licença [MIT](LICENSE).

## Desenvolvedor

**GSZ TV — GSZ**
GitHub: [@guisz-code](https://github.com/guisz-code)
