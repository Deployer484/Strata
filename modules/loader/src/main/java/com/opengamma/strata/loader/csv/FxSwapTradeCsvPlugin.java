/*
 * Copyright (C) 2018 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.loader.csv;

import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_1_CURRENCY_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_1_DIRECTION_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_1_NOTIONAL_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_1_PAYMENT_DATE_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_2_CURRENCY_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_2_DIRECTION_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_2_NOTIONAL_FIELD;
import static com.opengamma.strata.loader.csv.FxSingleTradeCsvPlugin.LEG_2_PAYMENT_DATE_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.BUY_SELL_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.CONVENTION_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.CURRENCY_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.FX_RATE_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.NOTIONAL_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.PAYMENT_DATE_CAL_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.PAYMENT_DATE_CNV_FIELD;
import static com.opengamma.strata.loader.csv.TradeCsvLoader.PAYMENT_DATE_FIELD;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.currency.FxRate;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.collect.io.CsvOutput.CsvRowOutputWithHeaders;
import com.opengamma.strata.collect.io.CsvRow;
import com.opengamma.strata.loader.LoaderUtils;
import com.opengamma.strata.product.TradeInfo;
import com.opengamma.strata.product.common.BuySell;
import com.opengamma.strata.product.fx.FxSingle;
import com.opengamma.strata.product.fx.FxSwap;
import com.opengamma.strata.product.fx.FxSwapTrade;

/**
 * Handles the CSV file format for FX Swap trades.
 */
class FxSwapTradeCsvPlugin implements TradeTypeCsvWriter<FxSwapTrade> {

  /**
   * The singleton instance of the plugin.
   */
  public static final FxSwapTradeCsvPlugin INSTANCE = new FxSwapTradeCsvPlugin();

  private static final String FAR = "Far ";
  private static final String FAR_FX_RATE_DATE_FIELD = "Far FX Rate";
  private static final String FAR_PAYMENT_DATE_FIELD = "Far Payment Date";

  /** The headers. */
  private static final ImmutableList<String> HEADERS = ImmutableList.<String>builder()
      .add(LEG_1_DIRECTION_FIELD)
      .add(LEG_1_PAYMENT_DATE_FIELD)
      .add(LEG_1_CURRENCY_FIELD)
      .add(LEG_1_NOTIONAL_FIELD)
      .add(LEG_2_DIRECTION_FIELD)
      .add(LEG_2_PAYMENT_DATE_FIELD)
      .add(LEG_2_CURRENCY_FIELD)
      .add(LEG_2_NOTIONAL_FIELD)
      .add(PAYMENT_DATE_CNV_FIELD)
      .add(PAYMENT_DATE_CAL_FIELD)
      .add(FAR + LEG_1_DIRECTION_FIELD)
      .add(FAR + LEG_1_PAYMENT_DATE_FIELD)
      .add(FAR + LEG_1_CURRENCY_FIELD)
      .add(FAR + LEG_1_NOTIONAL_FIELD)
      .add(FAR + LEG_2_DIRECTION_FIELD)
      .add(FAR + LEG_2_PAYMENT_DATE_FIELD)
      .add(FAR + LEG_2_CURRENCY_FIELD)
      .add(FAR + LEG_2_NOTIONAL_FIELD)
      .add(FAR + PAYMENT_DATE_CNV_FIELD)
      .add(FAR + PAYMENT_DATE_CAL_FIELD)
      .build();

  //-------------------------------------------------------------------------
  /**
   * Parses the data from a CSV row.
   *
   * @param row  the CSV row object
   * @param info  the trade info object
   * @param resolver  the resolver used to parse additional information
   * @return the parsed trade
   */
  static FxSwapTrade parse(CsvRow row, TradeInfo info, TradeCsvInfoResolver resolver) {
    FxSwapTrade trade = parseRow(row, info);
    return resolver.completeTrade(row, trade);
  }

  // parses the trade
  private static FxSwapTrade parseRow(CsvRow row, TradeInfo info) {
    if (row.findValue(CONVENTION_FIELD).isPresent() || row.findValue(BUY_SELL_FIELD).isPresent()) {
      return parseConvention(row, info);
    } else {
      return parseFull(row, info);
    }
  }

  // convention-based
  // ideally we'd use the trade date plus "period to start" to get the spot/payment date
  // but we don't have all the data and it gets complicated in places like TRY, RUB and AED
  private static FxSwapTrade parseConvention(CsvRow row, TradeInfo info) {
    CurrencyPair pair = CurrencyPair.parse(row.getValue(CONVENTION_FIELD));
    BuySell buySell = LoaderUtils.parseBuySell(row.getValue(BUY_SELL_FIELD));
    CurrencyAmount amount = buySell.normalize(CsvLoaderUtils.parseCurrencyAmount(row, CURRENCY_FIELD, NOTIONAL_FIELD));
    double nearFxRate = LoaderUtils.parseDouble(row.getValue(FX_RATE_FIELD));
    double farFxRate = LoaderUtils.parseDouble(row.getValue(FAR_FX_RATE_DATE_FIELD));
    LocalDate nearPaymentDate = LoaderUtils.parseDate(row.getValue(PAYMENT_DATE_FIELD));
    LocalDate farPaymentDate = LoaderUtils.parseDate(row.getValue(FAR_PAYMENT_DATE_FIELD));
    Optional<BusinessDayAdjustment> paymentAdj = FxSingleTradeCsvPlugin.parsePaymentDateAdjustment(row);

    FxRate nearRate = FxRate.of(pair, nearFxRate);
    FxRate farRate = FxRate.of(pair, farFxRate);
    FxSwap fx = paymentAdj
        .map(adj -> FxSwap.of(amount, nearRate, nearPaymentDate, farRate, farPaymentDate, adj))
        .orElseGet(() -> FxSwap.of(amount, nearRate, nearPaymentDate, farRate, farPaymentDate));
    return FxSwapTrade.of(info, fx);
  }

  // parse full definition
  private static FxSwapTrade parseFull(CsvRow row, TradeInfo info) {
    FxSingle nearFx = FxSingleTradeCsvPlugin.parseFxSingle(row, "");
    FxSingle farFx = FxSingleTradeCsvPlugin.parseFxSingle(row, "Far ");
    return FxSwapTrade.of(info, FxSwap.of(nearFx, farFx));
  }

  //-------------------------------------------------------------------------
  @Override
  public List<String> headers(List<FxSwapTrade> trades) {
    return HEADERS;
  }

  @Override
  public void writeCsv(CsvRowOutputWithHeaders csv, FxSwapTrade trade) {
    csv.writeCell(TradeCsvLoader.TYPE_FIELD, "FxSwap");
    FxSingleTradeCsvPlugin.INSTANCE.writeProduct(csv, "", trade.getProduct().getNearLeg());
    FxSingleTradeCsvPlugin.INSTANCE.writeProduct(csv, FAR, trade.getProduct().getFarLeg());
    csv.writeNewLine();
  }

  //-------------------------------------------------------------------------
  // Restricted constructor.
  private FxSwapTradeCsvPlugin() {
  }

}
