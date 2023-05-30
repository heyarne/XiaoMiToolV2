{
  inputs.nixpkgs.url = github:NixOS/nixpkgs;

  outputs = { self, nixpkgs, ... }@inputs: let
    system = builtins.currentSystem or "x86_64-linux";
    pkgs = import nixpkgs {
      inherit system;
      config = {
        android_sdk.accept_license = true;
        allowUnfreePredicate = pkg: builtins.elem (pkgs.lib.getName pkg) [
          "cmdline-tools"
          "tools"
        ];
      };
    };
    androidSdk = pkgs.androidenv.androidPkgs_9_0.androidsdk;
  in {
    devShells.${system}.default = pkgs.mkShell rec {
      buildInputs = with pkgs; [
        # basic android env
        androidSdk
        glibc

        # needed for the app
        xorg.libXtst
      ];
      # override the aapt2 that gradle uses with the nix-shipped version
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/28.0.3/aapt2";
      LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath buildInputs;
    };
  };
}
