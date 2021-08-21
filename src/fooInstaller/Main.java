package fooInstaller;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.net.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.io.*;
import java.lang.reflect.*;

import static mindustry.Vars.*;

public class Main extends Mod{

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            if (Version.combined().contains("Client")) { // Disable mod when running on client (cant remove mod within mod)
                Vars.mods.setEnabled(Vars.mods.getMod(this.getClass()), false);
                return;
            }
            BaseDialog dialog = new BaseDialog("Client Installer");
            if (mobile) { // Client doesn't work on mobile, bail out
                dialog.cont.add("[scarlet]Foo's client isn't available on mobile!");
                Vars.mods.setEnabled(Vars.mods.getMod(this.getClass()), false);
            } else {
                dialog.cont.add("[accent]Select a version of the client to download").colspan(2).row();
                dialog.cont.button("v6", () -> install("mindustry-antigrief/mindustry-client-v6-builds")).fillX();
                dialog.cont.button("v7", () -> install("mindustry-antigrief/mindustry-client-v7-builds")).fillX();
                dialog.addCloseButton();
            }
            dialog.show();
        });
    }

    void install(String url) {
        Method download = null;
        try {
            download = BeControl.class.getDeclaredMethod("download", String.class, Fi.class, Intc.class, Floatc.class, Boolp.class, Runnable.class, Cons.class);
            download.setAccessible(true);
        } catch (NoSuchMethodException e) { ui.showException(e); }
        if (download == null) return;
        Method finalDownload = download;

        Http.get("https://api.github.com/repos/" + url + "/releases/latest").error(e -> {
            ui.loadfrag.hide();
            Log.err(e);
        })
        .submit(res -> {
            Jval val = Jval.read(res.getResultAsString());
            String newBuild = val.getString("tag_name", "0");
            Jval asset = val.get("assets").asArray().find(v -> v.getString("name", "").toLowerCase().contains("desktop"));
            if (asset == null) asset = val.get("assets").asArray().find(v -> v.getString("name", "").toLowerCase().contains("mindustry"));
            if (asset == null) return;
            String updateUrl = asset.getString("browser_download_url", "");
            try{
                boolean[] cancel = {false};
                float[] progress = {0};
                int[] length = {0};
                Fi file = bebuildDirectory.child("client-be-" + newBuild + ".jar");
                Fi fileDest = OS.hasProp("becopy") ?
                        Fi.get(OS.prop("becopy")) :
                        Fi.get(BeControl.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

                BaseDialog dialog = new BaseDialog("@be.updating");
                finalDownload.invoke(becontrol, updateUrl, file, (Intc) i -> length[0] = i, (Floatc) v -> progress[0] = v, (Boolp)() -> cancel[0], (Runnable)() -> {
                    try{
                        Runtime.getRuntime().exec(OS.isMac ?
                                new String[]{"java", "-XstartOnFirstThread", "-DlastBuild=" + Version.build, "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()} :
                                new String[]{"java", "-DlastBuild=" + Version.build, "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()}
                        );
                        Core.app.exit();
                    }catch(IOException e){
                        ui.showException(e);
                    }
                }, (Cons<Throwable>)e -> {
                    dialog.hide();
                    ui.showException(e);
                });

                dialog.cont.add(new Bar(() -> length[0] == 0 ? Core.bundle.get("be.updating") : (int)(progress[0] * length[0]) / 1024/ 1024 + "/" + length[0]/1024/1024 + " MB", () -> Pal.accent, () -> progress[0])).width(400f).height(70f);
                dialog.buttons.button("@cancel", Icon.cancel, () -> {
                    cancel[0] = true;
                    dialog.hide();
                }).size(210f, 64f);
                dialog.setFillParent(false);
                dialog.show();
            }catch(Exception e){
                ui.showException(e);
            }
        });
    }
}
