package eu.fbk.st;

import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.kotlin.psi.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

public class FirebaseDbBChecker extends AnAction {


    private File jsonFile;
    private Project currentProject;
    private String rootDir;
    private Set<String> databasePathReferences;
    private Set<String> databaseUrlReferences;
    private Set<String> databaseDbReferences;
    private Set<String> firestoreDatabasePaths;
    private Set<String> firestoreUrlReferences;
    private String report;
    private boolean warn;
    private int progress = 1;
    private int total = 1;

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        currentProject = anActionEvent.getProject();
        rootDir = currentProject.getBasePath();
        report = "";
        warn = false;

        System.out.println("menu item clicked ");

        databasePathReferences = new HashSet<>();
        databaseUrlReferences = new HashSet<>();
        databaseDbReferences = new HashSet<>();
        firestoreDatabasePaths = new HashSet<>();
        firestoreUrlReferences = new HashSet<>();

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

                Set<String> paths = new HashSet<>();
                for (String pathRef : databasePathReferences) {
                    paths.add("/" + pathRef);
                    for (String dbRef : databaseDbReferences) {
                        String p = pathRef + "/" + dbRef;
                        paths.add(p.replace("//", "/"));
                    }
                }

                total = databaseUrlReferences.size() + paths.size() * databaseUrlReferences.size();

                firestoreDatabasePaths.add("");
                total += firestoreUrlReferences.size() * firestoreDatabasePaths.size();

                // add root path
                paths.add("");

                // Realtime
                report = checkDatabase(databaseUrlReferences, paths, "Firebase realtime", indicator);

                // Firestore
                report += checkDatabase(firestoreUrlReferences, firestoreDatabasePaths, "Firestore", indicator);

            }

            String checkDatabase(Set<String> urlCollection, Set<String> pathCollection, String dbType, ProgressIndicator indicator) {
                String localReport = "";

                for (String urlRef : urlCollection) {

                    for (String path : pathCollection) {

                        System.out.println("Checking " + path);

                        indicator.setText("Checking " + (progress++) + "/" + total + " possible paths...");

                        boolean readable = false;
                        boolean writable = false;


                        String fullRef = urlRef + path;

                        if (Utils.isReadable(urlRef, path)) {
                            readable = true;
                        }

                        if (Utils.isWritable(urlRef, path)) {
                            writable = true;
                        }


                        if (readable) {
                            warn = true;
                            if (writable) {
                                localReport += "<li>Your " + dbType + " database at <b><a href=\"" + fullRef + "\">" + fullRef + "</b> is <span style=\"color:red\">world readable and writable</span>!<br/> Please consider fixing your Security Rules before releasing your app.<br/></li>";
                            } else {
                                localReport += "<li>Your  " + dbType + "  database at <b><a href=\"" + fullRef + "\">" + fullRef + "</b> is <span style=\"color:red\">world readable</span>!<br/> If it's not intentional, please consider fixing your Security Rules before releasing your app.<br/></li>";
                            }
                        } else {
                            if (writable) {
                                localReport += "<li>Your " + dbType + "  database at <b><a href=\"" + fullRef + "\">" + fullRef + "</b> is <span style=\"color:red\">world writable but not readable</span>!<br/> Please consider fixing your Security Rules before releasing your app.<br/></li>";
                                warn = true;
                            } else {
                                localReport += "<li>Your " + dbType + "  atabase at <b><a href=\"" + fullRef + "\">" + fullRef + "</b> is not accessible for anonymous users!<br/></li>";
                            }
                        }
                    }
                }

                return localReport;
            }

            @Override
            public void onSuccess() {
                if (warn) {
                    report = "<html><body><ul>" + report + "</ul></body></html>";
                    showDialog(this.getClass().getResourceAsStream("/warn.png"), report, "Warning!");
                } else {

                    if (!report.equals("")) {
                        report = "<html><body><ul>" + report + "</ul></body></html>";
                        showDialog(this.getClass().getResourceAsStream("/ok.png"), report, "Root Database OK!");
                    } else {
                        report = "We didn't find Firebase Realtime database references!";
                        showDialog(this.getClass().getResourceAsStream("/ok.png"), report, "No Firebase Readltime DB Found!");
                    }

                }
            }
        };

        ProgressManager.getInstance().run(task);
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

            path = "https://" + projectID + ".firebaseio.com";

            databaseUrlReferences.add(path);

            path = "https://firestore.googleapis.com/v1beta1/projects/" + projectID + "/databases/(default)/documents/";

            firestoreUrlReferences.add(path);

        } catch (FileNotFoundException fnf) {
            // google-services.json not found?
            //  fnf.printStackTrace();
        } catch (JSONException je) {
            // mabye objects not found?
            //  je.printStackTrace();
        }
    }

    // get database/path references from source code
    private void getSourceDbRefereces() {
        ProjectFileIndex.SERVICE.getInstance(currentProject).iterateContent(new ContentIterator() {
            @Override
            public boolean processFile(@NotNull VirtualFile fileOrDir) {

                if (!fileOrDir.isDirectory()) {
                    PsiFile psiFile = PsiManager.getInstance(currentProject).findFile(fileOrDir);

                    try {
                        if (psiFile instanceof PsiJavaFile) {
                            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;

                            // invalidate old values
                            psiJavaFile.clearCaches();

                            psiJavaFile.accept(new MethodCallExpressionVisitor(databaseUrlReferences, databasePathReferences, databaseDbReferences, firestoreDatabasePaths));
                        } else if (psiFile instanceof KtFile) {

                            KtFile ktFile = (KtFile) psiFile;

                            List<KtImportDirective> ktImportDirectives = ktFile.getImportList().getImports();

                            // a dirty workaround to handle kotlin cases
                            // check if there an import from Firebase lib
                            for (KtImportDirective directive : ktImportDirectives) {
                                if (directive.getImportedFqName().asString().startsWith("com.google.firebase")) {
                                    ktFile.accept(new KotlinTreeVsitor(databaseUrlReferences, databasePathReferences, databaseDbReferences, firestoreDatabasePaths));

                                    // break. we have already collected some of the db references

                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return true;
            }
        });
    }
}
