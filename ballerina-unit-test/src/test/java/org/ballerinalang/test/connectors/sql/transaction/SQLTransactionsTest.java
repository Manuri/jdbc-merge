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
package org.ballerinalang.test.connectors.sql.transaction;

import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.test.database.utils.SQLDBUtils;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Class to test functionality of transactions in SQL.
 */
public class SQLTransactionsTest {

    private CompileResult result;
    private CompileResult resultMirror;
    private static final String DB_NAME = "TEST_SQL_CONNECTOR_TR";

    @BeforeClass
    public void setup() {
        result = BCompileUtil.compile("test-src/connectors/sql/transaction/sql_transaction_test.bal");
        resultMirror = BCompileUtil.compile("test-src/connectors/sql/transaction/mirror_table_transaction_test.bal");
        SQLDBUtils.deleteFiles(new File(SQLDBUtils.DB_DIRECTORY), DB_NAME);
        SQLDBUtils.initHSQLDBDatabase(SQLDBUtils.DB_DIRECTORY, DB_NAME, "datafiles/sql/SQLTableCreate.sql");
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransacton() {
        BValue[] returns = BRunUtil.invoke(result, "testLocalTransacton");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonRollback() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactonRollback");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonAbort() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactonAbort");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonThrow() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactonErrorThrow");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonThrowAndCatch() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactionErrorThrowAndCatch");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 1);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonCommitted() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactonCommitted");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testTwoTransactons() {
        BValue[] returns = BRunUtil.invoke(result, "testTwoTransactons");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 4);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonWithoutHandlers() {
        BValue[] returns = BRunUtil.invoke(result, "testTransactonWithoutHandlers");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonFailed() {
        BValue[] returns = BRunUtil.invoke(result, "testLocalTransactionFailed");
        Assert.assertEquals(returns.length, 2);
        Assert.assertEquals(returns[0].stringValue(), "beforetx inTrx inFld inTrx inFld inTrx inFld inTrx inFld "
                + "afterTrx");
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonSuccessWithFailed() {
        BValue[] returns = BRunUtil.invoke(result, "testLocalTransactonSuccessWithFailed");
        Assert.assertEquals(returns.length, 2);
        Assert.assertEquals(returns[0].stringValue(), "beforetx inTrx inFld inTrx inFld inTrx afterTrx");
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonFailedWithNextupdate() {
        BValue[] returns = BRunUtil.invoke(result, "testLocalTransactonFailedWithNextupdate");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
    }

    @Test(groups = "TransactionTest")
    public void testNestedTwoLevelTransactonSuccess() {
        BValue[] returns = BRunUtil.invoke(result, "testNestedTwoLevelTransactonSuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testNestedThreeLevelTransactonSuccess() {
        BValue[] returns = BRunUtil.invoke(result, "testNestedThreeLevelTransactonSuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 3);
    }

    @Test(groups = "TransactionTest", enabled = false) //Issue #7706
    public void testNestedThreeLevelTransactonFailed() {
        BValue[] returns = BRunUtil.invoke(result, "testNestedThreeLevelTransactonFailed");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testNestedThreeLevelTransactonFailedWithRetrySuccess() {
        BValue[] returns = BRunUtil.invoke(result, "testNestedThreeLevelTransactonFailedWithRetrySuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
        Assert.assertEquals(returns[2].stringValue(), "start txL1 txL2 txL3 txL3_Else txL3_Failed");
    }

    @Test(dependsOnGroups = "TransactionTest")
    public void testCloseConnectionPool() {
        BValue[] returns = BRunUtil.invoke(result, "testCloseConnectionPool");
        BInteger retValue = (BInteger) returns[0];
        Assert.assertEquals(retValue.intValue(), 1);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactionMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testLocalTransacton");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonRollbackMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactonRollback");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonAbortMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactonAbort");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonThrowMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactonErrorThrow");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonThrowAndCatchMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactionErrorThrowAndCatch");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 1);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonCommittedMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactonCommitted");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testTwoTransactonsMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTwoTransactons");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 1);
        Assert.assertEquals(((BInteger) returns[2]).intValue(), 4);
    }

    @Test(groups = "TransactionTest")
    public void testTransactonWithoutHandlersMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testTransactonWithoutHandlers");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonFailedMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testLocalTransactionFailed");
        Assert.assertEquals(returns.length, 2);
        Assert.assertEquals(returns[0].stringValue(), "beforetx inTrx inFld inTrx inFld inTrx inFld inTrx inFld "
                + "afterTrx");
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonSuccessWithFailedMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testLocalTransactonSuccessWithFailed");
        Assert.assertEquals(returns.length, 2);
        Assert.assertEquals(returns[0].stringValue(), "beforetx inTrx inFld inTrx inFld inTrx afterTrx");
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testLocalTransactonFailedWithNextupdateMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testLocalTransactonFailedWithNextupdate");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 1);
    }

    @Test(groups = "TransactionTest")
    public void testNestedTwoLevelTransactonSuccessMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testNestedTwoLevelTransactonSuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 2);
    }

    @Test(groups = "TransactionTest")
    public void testNestedThreeLevelTransactonSuccessMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testNestedThreeLevelTransactonSuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 3);
    }

    @Test(groups = "TransactionTest", enabled = false) //Issue #7706
    public void testNestedThreeLevelTransactonFailedMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testNestedThreeLevelTransactonFailed");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), -1);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
    }

    @Test(groups = "TransactionTest")
    public void testNestedThreeLevelTransactonFailedWithRetrySuccessMirrorTable() {
        BValue[] returns = BRunUtil.invoke(resultMirror, "testNestedThreeLevelTransactonFailedWithRetrySuccess");
        Assert.assertEquals(((BInteger) returns[0]).intValue(), 0);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), 0);
        Assert.assertEquals(returns[2].stringValue(), "start txL1 txL2 txL3 txL3_Else txL3_Failed");
    }

    @AfterSuite
    public void cleanup() {
        SQLDBUtils.deleteDirectory(new File(SQLDBUtils.DB_DIRECTORY));
    }
}
