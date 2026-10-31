# GSZ TV

**Um launcher leve, simples e sem anúncios para Android TV.**

O GSZ TV é um projeto de launcher desenvolvido com foco em praticidade, simplicidade e desempenho, oferecendo uma experiência de navegação mais limpa em dispositivos Android TV.

O projeto tem como referência o projetor **Magcubic HY300**, com Android 12.

## ✨ Características

- Interface simples e intuitiva.
- Visual limpo, sem anúncios.
- Navegação adaptada para televisão e controle remoto.
- Foco em leveza e desempenho.
- Projeto baseado no Free My TV, com personalizações do GSZ TV.

## 📱 Compatibilidade

- **Dispositivo de referência:** Magcubic HY300.
- **Sistema operacional:** Android 12.
- **Plataforma:** Android TV / dispositivos Android compatíveis.

A compatibilidade com outros dispositivos pode variar de acordo com a versão do Android e suas características.

## 🛠️ Compilação

Para compilar o projeto, você precisará de:

- Java JDK compatível com o projeto.
- Android SDK.
- Android Build Tools.
- ADB (Android Debug Bridge).
- Git.

### 1. Clonar o repositório

```bash
git clone https://github.com/guisz-code/GSZ-TV.git
```

Entre na pasta:

```bash
cd GSZ-TV
```

### 2. Configurar o ambiente

Configure as variáveis de ambiente necessárias para o Java e o Android SDK:

- `JAVA_HOME`: diretório de instalação do Java JDK.
- `ANDROID_HOME`: diretório do Android SDK.

Adicione também as ferramentas do Android SDK ao PATH, conforme necessário.

### 3. Compilar

Execute o script de compilação disponibilizado no projeto:

```bash
bash build.sh
```

O APK gerado deverá ser localizado no diretório de saída configurado pelo script.

> Observação: os requisitos exatos de versão do Java, Android SDK e Build Tools devem ser consultados na configuração do projeto.

## 📲 Instalação

Você pode instalar o APK em um dispositivo Android utilizando o ADB.

### Instalação por USB

Conecte o dispositivo ao computador com a depuração USB ativada e execute:

```bash
adb install caminho/do/arquivo.apk
```

### Instalação por Wi-Fi

1. Ative as opções de desenvolvedor no dispositivo.
2. Ative a depuração sem fio.
3. Consulte o endereço IP e a porta informados pelo Android.
4. Conecte utilizando:

```bash
adb connect IP_DO_DISPOSITIVO:PORTA
```

5. Instale o APK:

```bash
adb install caminho/do/arquivo.apk
```

## 📁 Estrutura do projeto

```text
GSZ-TV/
├── app/
├── .gitignore
├── build.sh
├── LICENSE
└── README.md
```

## ⚖️ Créditos e licença

Este projeto utiliza como referência o **Free My TV**.

Todos os créditos e avisos de licença dos componentes de terceiros devem ser preservados conforme suas respectivas licenças.

O GSZ TV é distribuído sob a licença MIT, conforme os termos disponíveis no arquivo `LICENSE`.

## 👨‍💻 Desenvolvedor

**GSZ TV — GSZ**

GitHub: https://github.com/guisz-code

---

*Desenvolvido com foco em simplicidade, leveza e uma melhor experiência de uso em dispositivos Android.*