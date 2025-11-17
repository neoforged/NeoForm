import net.minecraft.DetectedVersion;
import net.minecraft.client.main.Main;

import java.util.Properties;

public class StartClient {
    public static void main(String[] args) throws Exception {
        var version = DetectedVersion.tryDetectVersion().name();
        Main.main(new String[]{
                "--assetsDir", assetsRoot,
                "--assetIndex", assetIndex,
                "--accessToken", "0",
                "--offlineDeveloperMode",
                "--version", version
        });
    }
}
