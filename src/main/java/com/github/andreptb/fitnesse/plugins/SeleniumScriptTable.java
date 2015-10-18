
package com.github.andreptb.fitnesse.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import com.github.andreptb.fitnesse.SeleniumFixture;
import com.github.andreptb.fitnesse.util.FitnesseMarkup;

import fitnesse.slim.instructions.CallInstruction;
import fitnesse.slim.instructions.ImportInstruction;
import fitnesse.slim.instructions.Instruction;
import fitnesse.testsystems.slim.SlimTestContext;
import fitnesse.testsystems.slim.Table;
import fitnesse.testsystems.slim.results.SlimTestResult;
import fitnesse.testsystems.slim.tables.ScriptTable;
import fitnesse.testsystems.slim.tables.SlimAssertion;
import fitnesse.testsystems.slim.tables.SlimExpectation;

/**
 * Selenium table, works just like ScriptTable, but adds extra features such as step screenshots and provide extra information to the fixture, allowing wait behavior for check and ensure actions
 */
public class SeleniumScriptTable extends ScriptTable {

	/**
	 * JUL Logger instance
	 */
	private static final Logger LOGGER = Logger.getLogger(SeleniumScriptTable.class.getName());

	/**
	 * Table keyword constant
	 */
	public static final String TABLE_KEYWORD = "selenium";
	/**
	 * Table type constant
	 */
	private static final String TABLE_TYPE = SeleniumScriptTable.TABLE_KEYWORD + "Script";
	/**
	 * SeleniumFixture screenshot action
	 */
	private static final String SCREENSHOT_FIXTURE_ACTION = "screenshot";

	/**
	 * Fixture package to auto-import package
	 */
	private static final String SELENIUM_FIXTURE_PACKAGE_TO_IMPORT = "com.github.andreptb.fitnesse";

	/**
	 * Constant to reference {@link CallInstruction} args private field
	 */
	private static final String CALL_INSTRUCTION_METHODNAME_FIELD = "methodName";
	/**
	 * Constant to reference {@link CallInstruction} args private field
	 */
	private static final String CALL_INSTRUCTION_ARGS_FIELD = "args";
	/**
	 * Utility to process FitNesse markup
	 */
	private FitnesseMarkup fitnesseMarkup = new FitnesseMarkup();

	public SeleniumScriptTable(Table table, String id, SlimTestContext testContext) {
		super(table, id, testContext);
	}

	@Override
	protected String getTableType() {
		return SeleniumScriptTable.TABLE_TYPE;
	}

	@Override
	protected String getTableKeyword() {
		return SeleniumScriptTable.TABLE_KEYWORD;
	}

	/**
	 * Overrides start actor to force the use of Selenium Fixture. Auto imports selenium fixture if needed
	 */
	@Override
	protected List<SlimAssertion> startActor() {
		List<SlimAssertion> assertions = new ArrayList<>();
		assertions.add(makeAssertion(new ImportInstruction(ImportInstruction.INSTRUCTION, SeleniumScriptTable.SELENIUM_FIXTURE_PACKAGE_TO_IMPORT), SlimExpectation.NOOP_EXPECTATION));
		assertions.addAll(startActor(NumberUtils.INTEGER_ZERO, SeleniumFixture.class.getName(), NumberUtils.INTEGER_ZERO));
		return assertions;
	}

	@Override
	protected List<SlimAssertion> show(int row) {
		if (StringUtils.contains(this.table.getCellContents(NumberUtils.INTEGER_ONE, row), SeleniumScriptTable.SCREENSHOT_FIXTURE_ACTION)) {
			return showScreenshot(row);
		}
		return super.show(row);
	}

	/**
	 * Adds screenshot action to current stack
	 *
	 * @param row current table row the action occured
	 * @return asssertion Same collection passed as parameter, to reduce boilerplate code
	 */
	protected List<SlimAssertion> showScreenshot(int row) {
		int lastCol = this.table.getColumnCountInRow(row) - NumberUtils.INTEGER_ONE;
		return invokeAction(NumberUtils.INTEGER_ONE, lastCol, row, new ShowScreenshotExpectation(NumberUtils.INTEGER_ONE, row));
	}

	@Override
	protected List<SlimAssertion> ensure(int row) {
		List<SlimAssertion> assertions = super.ensure(row);
		injectValueInFirstArg(assertions, false, true);
		return assertions;
	}

	@Override
	protected List<SlimAssertion> reject(int row) {
		List<SlimAssertion> assertions = super.reject(row);
		injectValueInFirstArg(assertions, false, false);
		return assertions;
	}

	@Override
	protected List<SlimAssertion> checkAction(int row) {
		List<SlimAssertion> assertions = super.checkAction(row);
		injectResultToCheckInAction(row, assertions, false);
		return assertions;
	}

	@Override
	protected List<SlimAssertion> checkNotAction(int row) {
		List<SlimAssertion> assertions = super.checkNotAction(row);
		injectResultToCheckInAction(row, assertions, true);
		return assertions;
	}

	private void injectResultToCheckInAction(int row, List<SlimAssertion> assertions, boolean not) {
		String contentToCheck = this.fitnesseMarkup.clean(this.table.getCellContents(this.table.getColumnCountInRow(row) - 1, row));
		if (CollectionUtils.isEmpty(assertions) || StringUtils.isBlank(contentToCheck)) {
			return;
		}
		injectValueInFirstArg(assertions, not, contentToCheck);
	}

	private void injectValueInFirstArg(List<SlimAssertion> assertions, boolean not, Object contentToCheck) {
		Instruction instruction = SlimAssertion.getInstructions(assertions).get(NumberUtils.INTEGER_ZERO);
		try {
			String valueToInject = FitnesseMarkup.SELECTOR_VALUE_SEPARATOR + (not ? FitnesseMarkup.SELECTOR_VALUE_DENY_INDICATOR : StringUtils.EMPTY) + contentToCheck;
			Object args = FieldUtils.readField(instruction, SeleniumScriptTable.CALL_INSTRUCTION_ARGS_FIELD, true);
			Object[] argsToInject;
			if (args instanceof Object[] && ArrayUtils.getLength(args) > NumberUtils.INTEGER_ZERO) {
				argsToInject = (Object[]) args;
				argsToInject[NumberUtils.INTEGER_ZERO] += valueToInject;
			} else {
				argsToInject = ArrayUtils.toArray(valueToInject);
			}
			String methodName = Objects.toString(FieldUtils.readField(instruction, SeleniumScriptTable.CALL_INSTRUCTION_METHODNAME_FIELD, true));
			if (Objects.isNull(MethodUtils.getAccessibleMethod(SeleniumFixture.class, Objects.toString(methodName), ClassUtils.toClass(argsToInject)))) {
				SeleniumScriptTable.LOGGER.fine("Method for instruction not found on SeleniumFixture, injection aborted: " + instruction);
				return;
			}
			FieldUtils.writeField(instruction, SeleniumScriptTable.CALL_INSTRUCTION_ARGS_FIELD, argsToInject);
		} catch (IllegalArgumentException | ReflectiveOperationException e) {
			SeleniumScriptTable.LOGGER.log(Level.FINE, "Failed to inject check value using reflection", e);
		}
	}

	/**
	 * Expectation implementation to process screenshot of browser current state
	 */
	private class ShowScreenshotExpectation extends RowExpectation {

		public ShowScreenshotExpectation(int col, int row) {
			super(col, row);
		}

		/**
		 * Delegates screenshot markup to FitnesseMarkup and adds a new column to last action's row
		 *
		 * @param actual The screenshot image filename which fixture screenshot action returned
		 * @param expected Not used
		 * @return testResult Always SlimTestResult#plain()
		 */
		@Override
		protected SlimTestResult createEvaluationMessage(String actual, String expected) {
			try {
				String imgLink = SeleniumScriptTable.this.fitnesseMarkup.imgLink(actual, getTestContext().getPageToTest());
				if (StringUtils.isBlank(imgLink)) {
					return SlimTestResult.fail("failed to generate screenshot preview");
				}
				SeleniumScriptTable.this.table.substitute(getCol(), getRow(), imgLink);
				return SlimTestResult.plain();
			} catch (IOException e) {
				return SlimTestResult.fail("IO error while generating screenshot link\n" + ExceptionUtils.getStackTrace(e));
			}
		}
	}
}
