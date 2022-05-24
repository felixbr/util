package com.twitter.finagle.stats.exp

import com.twitter.finagle.stats.Metadata
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.StatsReceiver
import scala.annotation.varargs

private[twitter] object Expression {

  /**
   * Create a expression with a constant number in double
   */
  def apply(num: Double): Expression = ConstantExpression(num.toString)

  /**
   * Create a histogram expression
   * @param component a [[HistogramComponent]]
   */
  def apply(
    metadata: Metadata,
    component: HistogramComponent
  ): Expression = {
    metadata.toMetricBuilder match {
      case Some(metricBuilder) =>
        require(
          metricBuilder.metricType == HistogramType,
          "this method is for creating histogram expression")
        HistogramExpression(metricBuilder, component)
      case None =>
        NoExpression
    }
  }

  /**
   * Create an single expression wrapping a counter or gauge.
   * @param metadata  MetricBuilder instance
   * @param showRollup  If set true, inform the expression handler to render all tail metrics
   */
  def apply(metadata: Metadata, showRollup: Boolean = false): Expression = {
    metadata.toMetricBuilder match {
      case Some(metricBuilder) =>
        require(metricBuilder.metricType != HistogramType, "provide a component for histogram")
        MetricExpression(metricBuilder, showRollup)
      case None =>
        NoExpression
    }
  }

  /**
   * Represent an expression constructed from metrics in strings, user would need to provide
   * role and service name if needed to the [[ExpressionSchema]], otherwise remain unspecified.
   *
   * @note [[StringExpression]] is for conveniently creating expressions without getting a hold of
   *       [[StatsReceiver]] or [[MetricBuilder]], if they present or easy to access, users should
   *       prefer creating expression via Metric instances.
   *       StringExpressions are not guaranteed to be real metrics, please proceed with proper
   *       validation and testing.
   *
   * @param exprs a sequence of metrics in strings
   */
  def apply(exprs: Seq[String], isCounter: Boolean): Expression = StringExpression(exprs, isCounter)

  /**
   * Create an expression from creating a [[com.twitter.finagle.stats.Counter]]
   * with a given [[StatsReceiver]].
   *
   * @param statsReceiver  The [[StatsReceiver]] used by the caller to record metric.
   * @param name           The name to create the [[com.twitter.finagle.stats.Counter]].
   * @param showRollup     When true, create an expression with all tail metrics. The
   *                       default is false.
   * @param validateMetric When true, only create an expression if the counter with name
   *                       {@code name} has been registered with the given
   *                       {@code statsReceiver}, otherwise return `None`. When false,
   *                       always return an expression. The default value is false.
   * @return               An optional [[Expression]] carrying the counter metadata.
   *                       Return `None` if the metric of name {@code name} is not found
   *                       in the {@code statsReceiver}.
   */
  def counter(
    statsReceiver: StatsReceiver,
    name: Seq[String],
    showRollup: Boolean = false,
    validateMetric: Boolean = false
  ): Option[Expression] =
    if (validateMetric) None // return None for now, will add the validation in another change
    else Some(this(statsReceiver.counter(name: _*).metadata, showRollup))

  /**
   * Create an expression from creating a [[com.twitter.finagle.stats.Stat]] with
   * a given [[StatsReceiver]].
   *
   * @param statsReceiver      The [[StatsReceiver]] used by the caller to record metric.
   * @param name               The name to create the [[com.twitter.finagle.stats.Stat]].
   * @param histogramComponent A [[HistogramComponent]] used to create a histogram
   *                           expression.
   * @param validateMetric     When true, only create an expression if the counter with
   *                           name {@code name} has been registered with the given
   *                           {@code statsReceiver}, otherwise return `None`. When
   *                           false, always return an expression. The default value is
   *                           false.
   * @return                   An optional [[Expression]] carrying the stat metadata.
   *                           Return `None` if the metric of name {@code name} is not
   *                           found in the {@code statsReceiver}.
   */
  def stat(
    statsReceiver: StatsReceiver,
    name: Seq[String],
    histogramComponent: HistogramComponent,
    validateMetric: Boolean = false
  ): Option[Expression] =
    if (validateMetric) None // return None for now, will add the validation in another change
    else Some(this(statsReceiver.stat(name: _*).metadata, histogramComponent))

  /**
   * Create a list of expressions from creating a [[com.twitter.finagle.stats.Stat]]
   * for each [[HistogramComponent]] with a given [[StatsReceiver]].
   *
   * @param statsReceiver       The [[StatsReceiver]] used by the caller to record
   *                            metric.
   * @param name                The name to create the
   *                            [[com.twitter.finagle.stats.Stat]].
   * @param histogramComponents A list of [[HistogramComponent]] used to create a
   *                            histogram expression. The default is
   *                            [[DefaultExpression]].
   * @param validateMetric      When true, only create an expression if the counter with
   *                            name {@code name} has been registered with the given
   *                            {@code statsReceiver}, otherwise return `None`. When
   *                            false, always return an expression. The default value is
   *                            false.
   * @return                    A sequence of [[Expression]] carrying the stat metadata.
   *                            The order of the returned sequence is in correspondence
   *                            with the oder of the {@code histogramComponents}. Return
   *                            an empty sequence if the metric of name {@code name} is
   *                            not found in the {@code statsReceiver}.
   */
  def stats(
    statsReceiver: StatsReceiver,
    name: Seq[String],
    histogramComponents: Seq[HistogramComponent] = HistogramComponent.DefaultPercentiles,
    validateMetric: Boolean = false
  ): Seq[Expression] =
    if (validateMetric) {
      Seq.empty // return an empty list for now, will add the validation in another change
    } else {
      val statMetadata = statsReceiver.stat(name: _*).metadata
      histogramComponents.map(this(statMetadata, _))
    }
}

/**
 * Metrics with their arithmetical(or others) calculations
 */
private[twitter] sealed trait Expression {
  final def plus(other: Expression): Expression = func("plus", other)

  final def minus(other: Expression): Expression = func("minus", other)

  final def divide(other: Expression): Expression = func("divide", other)

  final def multiply(other: Expression): Expression =
    func("multiply", other)

  @varargs
  def func(name: String, rest: Expression*): Expression =
    FunctionExpression(name, this +: rest)
}

/**
 * Represents a constant double number
 */
case class ConstantExpression private[exp] (repr: String) extends Expression

/**
 * Represents compound metrics and their arithmetical(or others) calculations
 */
case class FunctionExpression private[exp] (fnName: String, exprs: Seq[Expression])
    extends Expression {
  require(exprs.size != 0, "Functions must have at least 1 argument")
}

/**
 * Represents the leaf metrics
 */
case class MetricExpression private[exp] (metricBuilder: MetricBuilder, showRollup: Boolean)
    extends Expression

/**
 * Represent a histogram expression with specified component, for example the average, or a percentile
 * @param component a [[HistogramComponent]]
 */
case class HistogramExpression private[exp] (
  metricBuilder: MetricBuilder,
  component: HistogramComponent)
    extends Expression

/**
 * Represent an expression constructed from metrics in strings
 * @see Expression#apply
 */
case class StringExpression private[exp] (exprs: Seq[String], isCounter: Boolean) extends Expression

case object NoExpression extends Expression {
  @varargs
  override def func(name: String, rest: Expression*): Expression = NoExpression
}
