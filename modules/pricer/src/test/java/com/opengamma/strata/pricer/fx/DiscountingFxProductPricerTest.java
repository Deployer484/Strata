/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.fx;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendars;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.ImmutableFxIndex;
import com.opengamma.strata.finance.fx.Fx;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;

/**
 * Test {@link DiscountingFxProductPricer}.
 */
@Test
public class DiscountingFxProductPricerTest {
  private static final FxMatrix FX_MATRIX = RatesProviderFxDataSets.fxMatrix();
  private static final RatesProvider PROVIDER = RatesProviderFxDataSets.createProvider();

  private static final Currency KRW = Currency.KRW;
  private static final Currency USD = Currency.USD;
  private static final LocalDate PAYMENT_DATE = LocalDate.of(2012, 5, 4);
  private static final double NOMINAL_USD = 100_000_000;
  private static final double FX_RATE = 1123.45;
  private static final FxIndex INDEX = ImmutableFxIndex.builder()
      .name("USD/KRW")
      .currencyPair(CurrencyPair.of(USD, KRW))
      .fixingCalendar(HolidayCalendars.USNY)
      .maturityDateOffset(DaysAdjustment.ofBusinessDays(2, HolidayCalendars.USNY))
      .build();
  private static final Fx FWD = Fx.of(CurrencyAmount.of(USD, NOMINAL_USD), FxRate.of(USD, KRW, FX_RATE), PAYMENT_DATE);
  private static final DiscountingFxProductPricer PRICER = DiscountingFxProductPricer.DEFAULT;
  private static final double TOL = 1.0e-12;
  private static final double EPS_FD = 1E-7;
  private static final RatesFiniteDifferenceSensitivityCalculator CAL_FD =
      new RatesFiniteDifferenceSensitivityCalculator(EPS_FD);

  public void test_presentValue() {
    MultiCurrencyAmount computed = PRICER.presentValue(FWD, PROVIDER);
    double expected1 = NOMINAL_USD * PROVIDER.discountFactor(USD, PAYMENT_DATE);
    double expected2 = -NOMINAL_USD * FX_RATE * PROVIDER.discountFactor(KRW, PAYMENT_DATE);
    assertEquals(computed.getAmount(USD).getAmount(), expected1, NOMINAL_USD * TOL);
    assertEquals(computed.getAmount(KRW).getAmount(), expected2, NOMINAL_USD * TOL);
  }

  public void test_presentValue_ended() {
    Fx fwd = Fx.of(CurrencyAmount.of(USD, NOMINAL_USD), FxRate.of(USD, KRW, FX_RATE), LocalDate.of(2011, 11, 2));
    MultiCurrencyAmount computed = PRICER.presentValue(fwd, PROVIDER);
    assertEquals(computed, MultiCurrencyAmount.empty());
  }

  public void test_currencyExposure() {
    MultiCurrencyAmount computed = PRICER.currencyExposure(FWD, PROVIDER);
    MultiCurrencyAmount expected = PRICER.presentValue(FWD, PROVIDER);
    assertEquals(computed, expected);
  }

  public void test_parSpread() {
    double spread = PRICER.parSpread(FWD, PROVIDER);
    Fx fwdSp = Fx.of(CurrencyAmount.of(USD, NOMINAL_USD), FxRate.of(USD, KRW, FX_RATE + spread), PAYMENT_DATE);
    MultiCurrencyAmount pv = PRICER.presentValue(fwdSp, PROVIDER);
    assertEquals(pv.convertedTo(USD, PROVIDER).getAmount(), 0d, NOMINAL_USD * TOL);
  }

  // TODO forwardRate

  public void test_presentValueSensitivity() {
    PointSensitivities point = PRICER.presentValueSensitivity(FWD, PROVIDER);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities expectedUsd = CAL_FD.sensitivity(
        (ImmutableRatesProvider) PROVIDER, (p) -> PRICER.presentValue(FWD, (p)).getAmount(USD));
    CurveCurrencyParameterSensitivities expectedKrw = CAL_FD.sensitivity(
        (ImmutableRatesProvider) PROVIDER, (p) -> PRICER.presentValue(FWD, (p)).getAmount(KRW));
    assertTrue(computed.equalWithTolerance(expectedUsd.combinedWith(expectedKrw), NOMINAL_USD * FX_RATE * EPS_FD));
  }

  public void test_presentValueSensitivity_ended() {
    Fx fwd = Fx.of(CurrencyAmount.of(USD, NOMINAL_USD), FxRate.of(USD, KRW, FX_RATE), LocalDate.of(2011, 11, 2));
    PointSensitivities computed = PRICER.presentValueSensitivity(fwd, PROVIDER);
    assertEquals(computed, PointSensitivities.empty());
  }

}