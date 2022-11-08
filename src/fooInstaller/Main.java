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
import java.net.*;

import static mindustry.Vars.*;

public class Main extends Mod{
    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            // If the foo's field exists then we are already using the client and don't want to run the installer
            if (Structs.contains(Version.class.getDeclaredFields(), var -> var.getName().equals("foos"))) return;

            var d = new BaseDialog("@fooinstaller.title");

            if (mobile) { // Client doesn't work on mobile, bail out
                d.cont.add("@fooinstaller.mobile");
                Vars.mods.setEnabled(Vars.mods.getMod(this.getClass()), false);
            } else {
                d.cont.add("@fooinstaller.source").row();
                var def = "mindustry-antigrief/mindustry-client-v7-builds";
                var field = d.cont.field("", null).fillX().get();
                field.setMessageText(def);
                d.cont.row().button("@fooinstaller.confirm", () -> install(field.getText().trim().isEmpty() ? def : field.getText()));
            }
            d.addCloseButton();
            d.buttons.button("@fooinstaller.discord", Icon.discord, () -> Core.app.openURI("https://discord.gg/yp9ZW7j")).wrapLabel(false);
            d.show();
        });
    }

    void install(String url) {
        Http.get("https://api.github.com/repos/" + url + "/releases/latest").error(e -> {
            ui.loadfrag.hide();
            Log.err(e);
        }).submit(res -> {
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

                var d = new BaseDialog("@be.updating");
                download(updateUrl, file, i -> length[0] = i, v -> progress[0] = v, () -> cancel[0], () -> {
                    try{
                        Runtime.getRuntime().exec(OS.isMac ?
                            new String[]{javaPath, "-XstartOnFirstThread", "-DlastBuild=" + Version.build, "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()} :
                            new String[]{javaPath, "-DlastBuild=" + Version.build, "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()}
                        );
                        Core.app.exit();
                    }catch(IOException e){
                        d.cont.clearChildren();
                        d.cont.add("@fooinstaller.java.notfound").row();
                        d.cont.button("@fooinstaller.java.install", () -> Core.app.openURI("https://adoptium.net/index.html?variant=openjdk16&jvmVariant=hotspot")).size(210f, 64f);
                    }
                }, e -> {
                    d.hide();
                    ui.showException(e);
                });

                d.cont.add(new Bar(() -> length[0] == 0 ? Core.bundle.get("be.updating") : (int)(progress[0] * length[0]) / 1024/ 1024 + "/" + length[0]/1024/1024 + " MB", () -> Pal.accent, () -> progress[0])).width(400f).height(70f);
                d.buttons.button("@cancel", Icon.cancel, () -> {
                    cancel[0] = true;
                    d.hide();
                }).size(210f, 64f);
                d.buttons.button("@fooinstaller.discord", Icon.discord, () -> Core.app.openURI("https://discord.gg/yp9ZW7j")).size(210f, 64f);
                d.setFillParent(false);
                d.show();
            }catch(Exception e){
                ui.showException(e);
            }
        });
    }

    /** Copied from BeControl since that one isnt public and using it reflectively is horrible. */
    void download(String furl, Fi dest, Intc length, Floatc progressor, Boolp canceled, Runnable done, Cons<Throwable> error){
        mainExecutor.submit(() -> {
            try{
                HttpURLConnection con = (HttpURLConnection)new URL(furl).openConnection();
                BufferedInputStream in = new BufferedInputStream(con.getInputStream());
                OutputStream out = dest.write(false, 4096);

                byte[] data = new byte[4096];
                long size = con.getContentLength();
                long counter = 0;
                length.get((int)size);
                int x;
                while((x = in.read(data, 0, data.length)) >= 0 && !canceled.get()){
                    counter += x;
                    progressor.get((float)counter / (float)size);
                    out.write(data, 0, x);
                }
                out.close();
                in.close();
                if(!canceled.get()) done.run();
            }catch(Throwable e){
                error.get(e);
            }
        });
    }
}
