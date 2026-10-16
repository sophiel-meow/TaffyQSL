{
  description = "TaffyQSL development environment (Android SDK + adb, no Android Studio)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f system);

      buildToolsVersion = "36.1.0";
      platformVersion = "36.1";
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              android_sdk.accept_license = true;
              allowUnfreePredicate = pkg: (pkg.meta.homepage or "") == "https://developer.android.com/tools";
            };
          };

          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ platformVersion ];
            buildToolsVersions = [ buildToolsVersion ];
            includeEmulator = false;
            includeSystemImages = false;
            includeNDK = false;
            includeSources = false;
          };

          androidSdk = androidComposition.androidsdk;
          sdkRoot = "${androidSdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.jdk21
              pkgs.android-tools
            ];

            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;
            JAVA_HOME = pkgs.jdk21.home;

            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

            shellHook = ''
              echo "TaffyQSL dev shell — ANDROID_HOME=$ANDROID_HOME"
            '';
          };
        }
      );
    };
}
