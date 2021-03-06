/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class PropagatorConfiguration {

  static ContextPropagators configurePropagators(ConfigProperties config) {
    Map<String, TextMapPropagator> spiPropagators =
        StreamSupport.stream(
                ServiceLoader.load(ConfigurablePropagatorProvider.class).spliterator(), false)
            .collect(
                Collectors.toMap(
                    ConfigurablePropagatorProvider::getName,
                    ConfigurablePropagatorProvider::getPropagator));

    Set<TextMapPropagator> propagators = new LinkedHashSet<>();
    for (String propagatorName : config.getCommaSeparatedValues("otel.propagators")) {
      propagators.add(getPropagator(propagatorName, spiPropagators));
    }

    return ContextPropagators.create(TextMapPropagator.composite(propagators));
  }

  private static TextMapPropagator getPropagator(
      String name, Map<String, TextMapPropagator> spiPropagators) {
    if (name.equals("tracecontext")) {
      return W3CTraceContextPropagator.getInstance();
    }
    if (name.equals("baggage")) {
      return W3CBaggagePropagator.getInstance();
    }

    // Other propagators are in the extension artifact. Check one of the propagators.
    ClasspathUtil.checkClassExists(
        "io.opentelemetry.extension.trace.propagation.B3Propagator",
        name + " propagator",
        "opentelemetry-extension-trace-propagators");

    switch (name) {
      case "b3":
        return B3Propagator.getInstance();
      case "b3multi":
        return B3Propagator.builder().injectMultipleHeaders().build();
      case "jaeger":
        return JaegerPropagator.getInstance();
        // NB: https://github.com/open-telemetry/opentelemetry-specification/pull/1406
      case "ottrace":
        return OtTracePropagator.getInstance();
      default:
        TextMapPropagator spiPropagator = spiPropagators.get(name);
        if (spiPropagator != null) {
          return spiPropagator;
        }
        throw new ConfigurationException("Unrecognized value for otel.propagators: " + name);
    }
  }

  private PropagatorConfiguration() {}
}
