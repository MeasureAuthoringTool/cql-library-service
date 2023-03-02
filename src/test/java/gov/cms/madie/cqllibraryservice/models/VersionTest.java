package gov.cms.madie.cqllibraryservice.models;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import gov.cms.madie.models.common.Version;

import org.junit.jupiter.api.Test;

class VersionTest {

  @Test
  public void testToStringFormatsRevisionZero() {
    final Version v = new Version(1, 2, 0);
    assertThat(v.toString(), is(equalTo("1.2.000")));
  }

  @Test
  public void testToStringFormatsRevisionNumberLeadingZeros() {
    final Version v = new Version(1, 2, 4);
    assertThat(v.toString(), is(equalTo("1.2.004")));
  }

  @Test
  public void testToStringFormatsRevisionNumberLeadingTrailingZeros() {
    final Version v = new Version(1, 2, 40);
    assertThat(v.toString(), is(equalTo("1.2.040")));
  }

  @Test
  public void testParseReturnsDefaultVersionForNull() {
    final Version output = Version.parse(null);
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(0)));
    assertThat(output.getMinor(), is(equalTo(0)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("0.0.000")));
  }

  @Test
  public void testParseReturnsDefaultVersionForEmpty() {
    final Version output = Version.parse("");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(0)));
    assertThat(output.getMinor(), is(equalTo(0)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("0.0.000")));
  }

  @Test
  public void testParseTreatsSingleDigitAsMajor() {
    final Version output = Version.parse("1");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(1)));
    assertThat(output.getMinor(), is(equalTo(0)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("1.0.000")));
  }

  @Test
  public void testParseTreatsSingleDigitAsMajorWithDecimal() {
    final Version output = Version.parse("1.");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(1)));
    assertThat(output.getMinor(), is(equalTo(0)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("1.0.000")));
  }

  @Test
  public void testParseTreatsTwoDigitsAsMajorMinor() {
    final Version output = Version.parse("1.30");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(1)));
    assertThat(output.getMinor(), is(equalTo(30)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("1.30.000")));
  }

  @Test
  public void testParseTreatsTwoDigitsAsMajorMinorDecimal() {
    final Version output = Version.parse("1.05.");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(1)));
    assertThat(output.getMinor(), is(equalTo(5)));
    assertThat(output.getRevisionNumber(), is(equalTo(0)));
    assertThat(output.toString(), is(equalTo("1.5.000")));
  }

  @Test
  public void testParseHandlesFullVersionNumber() {
    final Version output = Version.parse("1.05.009");
    assertThat(output, is(notNullValue()));
    assertThat(output.getMajor(), is(equalTo(1)));
    assertThat(output.getMinor(), is(equalTo(5)));
    assertThat(output.getRevisionNumber(), is(equalTo(9)));
    assertThat(output.toString(), is(equalTo("1.5.009")));
  }
}
