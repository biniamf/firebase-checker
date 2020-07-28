package eu.fbk.st;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext;
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo;

import java.lang.annotation.ElementType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KotlinTreeVsitor extends KtTreeVisitor<Boolean> {

    private Set<String> urlReferences;
    private Set<String> pathReferences;
    private Set<String> dbReferences;
    private Set<String> firestorePathReferences;

    Set<String> localPathReferences;

    public KotlinTreeVsitor(Set<String> databaseUrlReferences,
                            Set<String> databasePathReferences,
                            Set<String> databaseDbReferences,
                            Set<String> databaseFirestrorePaths
                       ) {
        this.urlReferences = databaseUrlReferences;
        this.pathReferences = databasePathReferences;
        this.dbReferences = databaseDbReferences;
        this.firestorePathReferences = databaseFirestrorePaths;

        localPathReferences = new HashSet<>();
    }

    @Override
    public Void visitCallExpression(@NotNull KtCallExpression expression, Boolean data) {

        String funName = expression.getCalleeExpression().getText();

        if (funName.equals("child") || funName.equals("getReferenceFromUrl") ||
                funName.equals("document") ||  funName.equals("collection")  ||
                funName.equals("getReference")) {

            List<KtValueArgument> arguments = expression.getValueArguments();

            if (arguments.size() == 1) {
                String arg = arguments.get(0).getText().replace("\"", "");



                if (funName.equals("getReferenceFromUrl")) {
                    String regex = "((https?:\\/\\/[-a-zA-Z0-9]+\\.firebaseio\\.com\\/?)([-a-zA-Z0-9@:%_\\+.~#?&\\/=]*))";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(arg);
                    if (matcher.find()) {
                        String firebaseUrl = matcher.group(1);

                        urlReferences.add(firebaseUrl);
                    }
                } else if (funName.equals("collection") || funName.equals("document")) {
                    firestorePathReferences.add(arg + "/");
                } else {
                    // paths
                    // consider all paths in case of chain
                    for (String path : pathReferences) {
                        firestorePathReferences.add(path + arg + "/");
                    }
                    if (localPathReferences.size() > 0) {
                        pathReferences.addAll(localPathReferences);
                        localPathReferences.clear();
                    }
                    pathReferences.add(arg + "/");
                }
            }
        }

        return super.visitCallExpression(expression, data);
    }


}
