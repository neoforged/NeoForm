import net.minecraft.DetectedVersion;
import net.minecraft.client.main.Main;

import java.util.Properties;
import java.util.stream.Stream;

public class StartClient {
    public static void main(String[] args) throws Exception {
        var version = DetectedVersion.tryDetectVersion().name();
        String[] defaultArgs = new String[] {
            "--assetsDir", "{{assets_root}}",
            "--assetIndex", "{{asset_index}}",
            "--accessToken", "0",
            "--offlineDeveloperMode",
            "--version", version
        };
        Main.main(Stream.concat(Stream.of(defaultArgs), Stream.of(args)).toArray(String[]::new));
    }
}
