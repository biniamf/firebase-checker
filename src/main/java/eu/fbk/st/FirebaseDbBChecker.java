package eu.fbk.st;

import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.actions.DependenciesHandlerBase;
import com.intellij.psi.*;
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class FirebaseDbBChecker extends AnAction {


    private File jsonFile;
    private Project currentProject;
    private String rootDir;
    private Set<String> databasePathReferences;
    private Set<String> databaseUrlReferences;
    private Set<String> databaseDbReferences;
    private String report = "<html><ul>";
    private boolean warn = false;

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        currentProject = anActionEvent.getProject();
        rootDir = currentProject.getBasePath();

        databasePathReferences = new HashSet<>();
        databaseUrlReferences = new HashSet<>();
        databaseDbReferences = new HashSet<>();

        checkFirebaseDb();
    }

    private void checkFirebaseDb() {

        getConfigDbReference();
        getSourceDbRefereces();

        NotificationGroup notificationGroup = new NotificationGroup("plugin", NotificationDisplayType.TOOL_WINDOW, true);

        notificationGroup.createNotification(currentProject.getName(), "Checking your Firebase realtime database...", NotificationType.INFORMATION, (NotificationListener) null).notify(currentProject);

        final Task task = new Task.Backgroundable(currentProject, "Checking your Firebase realtime database...", true, new PerformAnalysisInBackgroundOption(currentProject)) {
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                // add root path
                databasePathReferences.add("");

                Set<String> paths = new HashSet<>();
                for (String pathRef : databasePathReferences) {
                    for (String dbRef : databaseDbReferences) {
                        String p = pathRef + "/" + dbRef;
                        paths.add(p.replace("//","/"));
                    }
                }

                int progress = 1;
                int total = paths.size() * databaseUrlReferences.size();

                for (String urlRef : databaseUrlReferences) {
                    for (String path : paths) {

                        indicator.setText("Checking " + (progress ++) + "/" + total + " possible paths...");

                        boolean readable = false;
                        boolean writable = false;

                        String fullRef = (urlRef + path).replace("//","/");

                        if (Utils.isReadable(urlRef, path)) {
                            readable = true;
                        }

                        if (Utils.isWritable(urlRef, path)) {
                            writable = true;
                        }

                        if (readable) {
                            warn = true;
                            if (writable) {
                                report += "<li>Your Firebase realtime database at <b>" + fullRef + "</b> is <span style=\"color:red\">world readable and writable</span>!<br/> Please consider fixing your Security Rules before releasing your app.<br/></li>";
                            } else {
                                report += "<li>Your Firebase realtime database at <b>" + fullRef + "</b> is <span style=\"color:red\">world readable</span>!<br/> If it's not intentional, please consider fixing your Security Rules before releasing your app.<br/></li>";
                            }
                        } else {
                            if (writable) {
                                report += "<li>Your Firebase realtime database at <b>" + fullRef + "</b> is <span style=\"color:red\">world writable but not readable</span>!<br/> Please consider fixing your Security Rules before releasing your app.<br/></li>";
                                warn = true;
                            } else {
                                report += "<li>Your Firebase realtime database at <b>" + fullRef + "</b> is not accessible for anonymous users!<br/></li>";
                            }
                        }

                    }
                }
                report += "</ul></html>";
            }

            @Override
            public void onSuccess() {
                if (warn) {
                    showDialog(this.getClass().getResourceAsStream("/warn.png"), report, "Warning!");
                } else {
                    showDialog(this.getClass().getResourceAsStream("/ok.png"), report, "Root Database OK!");
                }
            }
        };

        ProgressManager.getInstance().run(task);


//        if (readable) {
//            if (writable) {
//                showDialog(this.getClass().getResourceAsStream("/warn.png"), "Your Firebase realtime database is world writable! Please consider fixing your Security Rules before releasing your app.", "Warning!");
//            } else {
//                showDialog(this.getClass().getResourceAsStream("/warn.png"), "Your Firebase realtime database is world readable! If it's not intentional, please consider fixing your Security Rules before releasing your app.", "Warning!");
//            }
//        } else {
//            // in case reading is disabled but not writing
//            if (writable) {
//                showDialog(this.getClass().getResourceAsStream("/warn.png"), "Your Firebase realtime database is world writable but not readable! Please consider fixing your Security Rules before releasing your app.", "Warning!");
//            } else {
//                showDialog(this.getClass().getResourceAsStream("/ok.png"), "Your root Firebase realtime database is not accessible!", "Root Database OK!");
//            }
//        }
    }

    private void showDialog(InputStream inputStream, String msg, String title) {
        Notice panel = new Notice(inputStream, msg);
        DialogBuilder dialogBuilder = new DialogBuilder().title(title).centerPanel(panel.getRootPanel());
        dialogBuilder.addOkAction().setText("OK");

        dialogBuilder.setOkOperation(() -> {
            dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);

        });
        dialogBuilder.show();
    }

    // get database/path reference from Google services config file
    private void getConfigDbReference() {
        String path = null;
        String projectID;
        try {

            String googleServicesJson = rootDir + "/app/google-services.json";

            jsonFile = new File(googleServicesJson);

            String jsonContent = "";
            Scanner reader = new Scanner(jsonFile);
            while (reader.hasNextLine()) {
                jsonContent += reader.nextLine();
            }
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonContent);
            projectID = jsonObject.getJSONObject("project_info").getString("project_id");

            path = "https://" + projectID + ".firebaseio.com/";

            databaseUrlReferences.add(path);
        } catch (FileNotFoundException fnf) {
            // google-services.json not found?
            fnf.printStackTrace();
        }   catch (JSONException je) {
            // response isn't json?
            je.printStackTrace();
        }
    }

    // get database/path references from source code
    private void getSourceDbRefereces() {
        ProjectFileIndex.SERVICE.getInstance(currentProject).iterateContent(new ContentIterator() {
            @Override
            public boolean processFile(@NotNull VirtualFile fileOrDir) {

                if (!fileOrDir.isDirectory()) {
                    PsiFile psiFile = PsiManager.getInstance(currentProject).findFile(fileOrDir);

                    if (psiFile instanceof PsiJavaFile) {
                        PsiJavaFile psiFJavaile = (PsiJavaFile) PsiManager.getInstance(currentProject).findFile(fileOrDir);
                        psiFJavaile.accept(new MethodCallExpressionVisitor(databaseUrlReferences, databasePathReferences, databaseDbReferences));
                    }

                }

                return true;
            }
        });
    }
}
