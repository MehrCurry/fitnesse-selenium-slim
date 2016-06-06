
package com.github.andreptb.fitnesse;

import org.junit.runner.RunWith;

import fitnesse.junit.FitNesseRunner;

/**
 * Slim Fixture testing. Configured to run FitNesseSeleniumSlim.SeleniumFixtureTests suite.
 */
@RunWith(FitNesseRunner.class)
@FitNesseRunner.Suite(".FitNesseSeleniumSlim.SeleniumFixtureTests.SameBrowserSessionTests.DialogTests.AlertTest")
@FitNesseRunner.FitnesseDir("fitnesse")
@FitNesseRunner.OutputDir("target/fitnesse")
public class SeleniumFixtureTest {

}
