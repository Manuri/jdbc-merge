/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.net.grpc.util;

import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.launcher.LauncherUtils;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.debugger.Debugger;
import org.ballerinalang.util.diagnostic.Diagnostic;
import org.ballerinalang.util.diagnostic.DiagnosticListener;
import org.ballerinalang.util.program.BLangFunctions;
import org.testng.Assert;
import org.wso2.ballerinalang.compiler.Compiler;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.ballerinalang.compiler.CompilerOptionName.COMPILER_PHASE;
import static org.ballerinalang.compiler.CompilerOptionName.PRESERVE_WHITESPACE;
import static org.ballerinalang.compiler.CompilerOptionName.SOURCE_ROOT;

/**
 * Utility methods for unit tests.
 *
 * @since 0.964.0
 */
public class BTestUtils {

    private static Path resourceDir = Paths.get(
            BTestUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath());

    /**
     * Compile and return the semantic errors.
     *
     * @param sourceFilePath Path to source package/file
     * @return Semantic errors
     */
    public static CompileResult compile(String sourceFilePath) {
        return compile(sourceFilePath, CompilerPhase.CODE_GEN);
    }

    /**
     * Compile and return the semantic errors.
     *
     * @param sourceRoot  root path of the source packages
     * @param packageName name of the package to compile
     * @return Semantic errors
     */
    public static CompileResult compile(String sourceRoot, String packageName) {
        try {
            String effectiveSource;
            Path rootPath = Paths.get(BTestUtils.class.getProtectionDomain().getCodeSource()
                                              .getLocation().toURI().getPath().concat(sourceRoot));
            if (Files.isDirectory(Paths.get(packageName))) {
                String[] pkgParts = packageName.split("\\/");
                List<Name> pkgNameComps = Arrays.stream(pkgParts)
                        .map(part -> {
                            if (part.equals("")) {
                                return Names.EMPTY;
                            } else if (part.equals("_")) {
                                return Names.EMPTY;
                            }
                            return new Name(part);
                        })
                        .collect(Collectors.toList());
                PackageID pkgId = new PackageID(Names.ANON_ORG, pkgNameComps, Names.DEFAULT_VERSION);
                effectiveSource = pkgId.getName().getValue();
            } else {
                effectiveSource = packageName;
            }
            return compile(rootPath.toString(), effectiveSource, CompilerPhase.CODE_GEN);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("error while running test: " + e.getMessage());
        }
    }

    /**
     * Compile and return the semantic errors.
     *
     * @param sourceFilePath Path to source package/file
     * @param compilerPhase  Compiler phase
     * @return Semantic errors
     */
    public static CompileResult compile(String sourceFilePath, CompilerPhase compilerPhase) {
        Path sourcePath = Paths.get(sourceFilePath);
        String packageName = sourcePath.getFileName().toString();
        Path sourceRoot = resourceDir.resolve(sourcePath.getParent());
        return compile(sourceRoot.toString(), packageName, compilerPhase);
    }

    /**
     * Compile and return the semantic errors.
     *
     * @param sourceRoot    root path of the source packages
     * @param packageName   name of the package to compile
     * @param compilerPhase Compiler phase
     * @return Semantic errors
     */
    public static CompileResult compile(String sourceRoot, String packageName, CompilerPhase compilerPhase) {
        CompilerContext context = new CompilerContext();
        CompilerOptions options = CompilerOptions.getInstance(context);
        options.put(SOURCE_ROOT, resourceDir.resolve(sourceRoot).toString());
        options.put(COMPILER_PHASE, compilerPhase.toString());
        options.put(PRESERVE_WHITESPACE, "false");

        CompileResult comResult = new CompileResult();

        // catch errors
        DiagnosticListener listener = comResult::addDiagnostic;
        context.put(DiagnosticListener.class, listener);

        // compile
        Compiler compiler = Compiler.getInstance(context);
        compiler.compile(packageName);
        org.wso2.ballerinalang.programfile.ProgramFile programFile = compiler.getCompiledProgram();
        if (programFile != null) {
            comResult.setProgFile(LauncherUtils.getExecutableProgram(programFile));
        }

        return comResult;
    }
    
    /**
     * Invoke a ballerina function.
     *
     * @param compileResult CompileResult instance
     * @param packageName   Name of the package to invoke
     * @param functionName  Name of the function to invoke
     * @param args          Input parameters for the function
     * @return return values of the function
     */
    public static BValue[] invoke(CompileResult compileResult, String packageName, String functionName,
                                  BValue[] args) {
        if (compileResult.getErrorCount() > 0) {
            throw new IllegalStateException("compilation contains errors.");
        }
        ProgramFile programFile = compileResult.getProgFile();
        Debugger debugger = new Debugger(programFile);
        programFile.setDebugger(debugger);
        return BLangFunctions.invokeEntrypointCallable(programFile, packageName, functionName, args);
    }

    /**
     * Invoke a ballerina function.
     *
     * @param compileResult CompileResult instance
     * @param packageName   Name of the package to invoke
     * @param functionName  Name of the function to invoke
     * @return return values of the function
     */
    public static BValue[] invoke(CompileResult compileResult, String packageName, String functionName) {
        BValue[] args = {};
        return invoke(compileResult, packageName, functionName, args);
    }

    /**
     * Invoke a ballerina function.
     *
     * @param compileResult CompileResult instance
     * @param functionName  Name of the function to invoke
     * @param args          Input parameters for the function
     * @return return values of the function
     */
    public static BValue[] invoke(CompileResult compileResult, String functionName, BValue[] args) {
        if (compileResult.getErrorCount() > 0) {
            throw new IllegalStateException("compilation contains errors.");
        }
        ProgramFile programFile = compileResult.getProgFile();
        Debugger debugger = new Debugger(programFile);
        programFile.setDebugger(debugger);
        return BLangFunctions.invokeEntrypointCallable(programFile, programFile.getEntryPkgName(), functionName, args);
    }

    /**
     * Invoke a ballerina function.
     *
     * @param compileResult CompileResult instance
     * @param functionName  Name of the function to invoke
     * @return return values of the function
     */
    public static BValue[] invoke(CompileResult compileResult, String functionName) {
        BValue[] args = {};
        return invoke(compileResult, functionName, args);
    }

    /**
     * Assert an error.
     *
     * @param result          Result from compilation
     * @param errorIndex      Index of the error in the result
     * @param expectedErrMsg  Expected error message
     * @param expectedErrLine Expected line number of the error
     * @param expectedErrCol  Expected column number of the error
     */
    public static void validateError(CompileResult result, int errorIndex, String expectedErrMsg, int expectedErrLine,
                                     int expectedErrCol) {
        Diagnostic diag = result.getDiagnostics()[errorIndex];
        Assert.assertEquals(diag.getMessage(), expectedErrMsg, "incorrect error message:");
        Assert.assertEquals(diag.getPosition().getStartLine(), expectedErrLine, "incorrect line number:");
        Assert.assertEquals(diag.getPosition().getStartColumn(), expectedErrCol, "incorrect column position:");
    }
}
