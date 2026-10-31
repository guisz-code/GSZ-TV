# GSZ TV

Launcher leve, sem anúncios e pensado para o projetor **Magcubic HY300 com Android 12**.

O GSZ TV mantém os aplicativos do usuário em primeiro plano e inclui acessos diretos para o que faz parte do uso real do projetor:

- Entrada HDMI;
- Menu nativo de projeção do HY300 — correção de cantos/keystone, rotação e ajustes de imagem;
- Configurações do Android;
- Central de desempenho, com status de RAM e armazenamento e limpeza configurável de apps em segundo plano;
- Continuar assistindo com capa, cores predominantes e gradiente adaptado ao tema;
- Alternância reversível entre GSZ TV e o launcher original MovieArk.

## Instalar no HY300

Com o projetor conectado por ADB:

```powershell
$adb="C:\\Users\\Guisz\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\\platform-tools\\adb.exe"
& $adb connect 192.168.1.22:13111
& $adb -s 192.168.1.22:13111 install -r GSZTV.apk
```

## Compilar

Requer JDK 17 e Android SDK com `build-tools` e plataforma Android 34.

```powershell
$env:JAVA_HOME="C:\\Program Files\\Microsoft\\jdk-17.0.21.21-hotspot"
$env:ANDROID_HOME="C:\\Users\\Guisz\\Documents\\Codex\\2026-10-30\\atue-como-um-desenvolvedor-android-expert\\outputs\\GSZ-TV\\android-sdk"
& "C:\\Program Files\\Git\\bin\\bash.exe" ./build.sh --generic
```

O APK gerado é `GSZTV.apk`.

## Créditos

O GSZ TV é um projeto independente e específico para o HY300, desenvolvido a partir do código aberto do [Free My TV](https://github.com/simbasjungles/free-my-tv), de seus contribuidores. A base original é licenciada sob a licença MIT; por isso, o aviso de copyright e a licença original são preservados em [LICENSE](LICENSE).

As modificações para o GSZ TV incluem identidade visual, integração com os menus nativos do HY300, gestão reversível do MovieArk, Central de Desempenho e melhorias no "Continuar assistindo".

## Licença

MIT. Consulte [LICENSE](LICENSE).
