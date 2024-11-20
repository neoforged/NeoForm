package mcp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.main.Main;

public class Start
{
    public static void main(String[] args)
    {
        /*
         * start minecraft game application
         * --version is just used as 'launched version' in snoop data and is required
         * Working directory is used as gameDir if not provided
         */
        String assets = null;
        String assetIndex = "17";
        try (InputStream assetsInput = Start.class.getClassLoader().getResourceAsStream("assets.json")) {
            if (assetsInput != null) {
                JsonElement element = JsonParser.parseReader(new InputStreamReader(assetsInput));
                assets = element.getAsJsonObject().get("assets").getAsString();
                assetIndex = element.getAsJsonObject().get("asset_index").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (assets == null) {
            assets = System.getenv().containsKey("assetDirectory") ? System.getenv("assetDirectory") : "assets";
        }
        Main.main(concat(new String[] { "--version", "mcp", "--accessToken", "0", "--assetsDir", assets, "--assetIndex", assetIndex, "--userProperties", "{}" }, args));
    }

    public static <T> T[] concat(T[] first, T[] second)
    {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
