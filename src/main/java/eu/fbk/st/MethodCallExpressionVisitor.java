package eu.fbk.st;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.*;
import com.intellij.vcs.log.Hash;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodCallExpressionVisitor extends JavaRecursiveElementVisitor {

    private Set<String> urlReferences;
    private Set<String> pathReferences;
    private Set<String> dbReferences;
    private Set<String> databaseFirestorePaths;

    public MethodCallExpressionVisitor(Set<String> urlReferences,
                                       Set<String> pathReferences,
                                       Set<String> dbReferences,
                                       Set<String> databaseFirestorePaths) {
        this.urlReferences = urlReferences;
        this.pathReferences = pathReferences;
        this.dbReferences = dbReferences;
        this.databaseFirestorePaths = databaseFirestorePaths;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression callExpression) {
        ProgressIndicatorProvider.checkCanceled();

        super.visitCallExpression(callExpression);

        PsiMethod method = callExpression.resolveMethod();

        if (method == null) {
            return;
        }

        String methodSig = method.getSignature(PsiSubstitutor.EMPTY).getName();

        // Firebase realtime related
        if (methodSig.equals("getReference") ||
                methodSig.equals("collection") ||
                methodSig.equals("document") ||
                methodSig.equals("getReferenceFromUrl") ||
                methodSig.equals("child")) {

            PsiClass psiClass = method.getContainingClass();
            String parentClass = psiClass.getQualifiedName();

            if (parentClass.equals("com.google.firebase.database.FirebaseDatabase")) {
                PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();

                // path or db reference
                if (expressions.length == 1) {
                    PsiExpression psiExpression = expressions[0];

                    String param = psiExpression.getText().replace("\"", "");

                    // check if we have url or path
                    String regex = "((https?:\\/\\/[-a-zA-Z0-9]+\\.firebaseio\\.com\\/?)([-a-zA-Z0-9@:%_\\+.~#?&\\/=]*))";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(param);
                    if (matcher.find()) {
                        String firebaseUrl = matcher.group(1);

//                        if (!firebaseUrl.endsWith("/")) {
//                            firebaseUrl = firebaseUrl + "";
//                        }
                        urlReferences.add(firebaseUrl);
                    } else {
                        pathReferences.add(param + "/");
                    }
                }
            } else if (parentClass.equals("com.google.firebase.database.DatabaseReference")) {

                String path = "";
                // .child()

                while (true) {
                    try {
                        PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();

                        // path or db reference
                        // e.g., /text/text/text/.json
                        if (expressions.length == 1) {
                            PsiExpression psiExpression = expressions[0];

                            path += "/" + psiExpression.getText().replace("\"", "") + "/" ;

                            dbReferences.add(path);
                        }

                        // Dot
                        PsiElement nextSibling = callExpression.getNextSibling();
                        // Parenthesis
                        nextSibling = nextSibling.getNextSibling();
                        // Chained method name
                        nextSibling = nextSibling.getParent();
                        // Chained method
                        PsiElement chainedMethod = nextSibling.getParent();

                        if (chainedMethod instanceof PsiMethodCallExpression) {
                            callExpression = (PsiMethodCallExpression) chainedMethod;

                            PsiMethod childMethod = callExpression.resolveMethod();

                            if (!childMethod.getSignature(PsiSubstitutor.EMPTY).getName().equals("child")) {
                                break;
                            }
                        } else {
                            break;
                        }
                    } catch (Exception nullSb) {
                        break;
                    }
                }
            } else if (parentClass.equals("com.google.firebase.firestore.FirebaseFirestore")) {
                try {
                    throw new Exception("Not implemented yet");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }
}


