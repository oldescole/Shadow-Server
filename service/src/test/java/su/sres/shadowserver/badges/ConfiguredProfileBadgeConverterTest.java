/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.badges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import su.sres.shadowserver.configuration.BadgeConfiguration;
import su.sres.shadowserver.configuration.BadgesConfiguration;
import su.sres.shadowserver.entities.Badge;
import su.sres.shadowserver.storage.AccountBadge;

public class ConfiguredProfileBadgeConverterTest {

  private Clock clock;
  private ResourceBundleFactory resourceBundleFactory;
  private ResourceBundle resourceBundle;

  @BeforeEach
  private void beforeEach() {
    clock = mock(Clock.class);
    resourceBundleFactory = mock(ResourceBundleFactory.class, (invocation) -> {
      throw new UnsupportedOperationException();
    });

    when(clock.instant()).thenReturn(Instant.ofEpochSecond(42));
  }

  private static String idFor(int i) {
    return "Badge-" + i;
  }

  private static URL imageUrlFor(int i) {
    try {
      return new URL("https://example.com/badge/" + i);
    } catch (MalformedURLException e) {
      throw new AssertionError(e);
    }
  }

  private static String nameFor(int i) {
    return "TRANSLATED NAME " + i;
  }

  private static String desriptionFor(int i) {
    return "TRANSLATED DESCRIPTION " + i;
  }

  private static BadgeConfiguration newBadge(int i) {
    return new BadgeConfiguration(idFor(i), imageUrlFor(i), "other");
  }

  private BadgesConfiguration createBadges(int count) {
    List<BadgeConfiguration> badges = new ArrayList<>(count);
    Object[][] objects = new Object[count * 2][2];
    for (int i = 0; i < count; i++) {
      badges.add(newBadge(i));
      objects[(i * 2)] = new Object[]{idFor(i) + "_name", nameFor(i)};
      objects[(i * 2) + 1] = new Object[]{idFor(i) + "_description", desriptionFor(i)};
    }
    resourceBundle = new ListResourceBundle() {
      @Override
      protected Object[][] getContents() {
        return objects;
      }
    };
    return new BadgesConfiguration(badges, List.of());
  }

  private BadgeConfiguration getBadge(BadgesConfiguration badgesConfiguration, int i) {
    return badgesConfiguration.getBadges().stream()
        .filter(badgeConfiguration -> idFor(i).equals(badgeConfiguration.getId()))
        .findFirst().orElse(null);
  }

  private ArgumentCaptor<ResourceBundle.Control> setupResourceBundle(Locale expectedLocale) {
    ArgumentCaptor<ResourceBundle.Control> controlArgumentCaptor =
        ArgumentCaptor.forClass(ResourceBundle.Control.class);
    doReturn(resourceBundle).when(resourceBundleFactory).createBundle(
        eq(ConfiguredProfileBadgeConverter.BASE_NAME), eq(expectedLocale), controlArgumentCaptor.capture());
    return controlArgumentCaptor;
  }

  @Test
  void testConvertEmptyList() {
    BadgesConfiguration badgesConfiguration = createBadges(1);
    ConfiguredProfileBadgeConverter badgeConverter = new ConfiguredProfileBadgeConverter(clock, badgesConfiguration,
        resourceBundleFactory);
    assertThat(badgeConverter.convert(List.of(Locale.getDefault()), List.of())).isNotNull().isEmpty();
  }

  @ParameterizedTest
  @MethodSource
  void testNoLocales(String name, Instant expiration, boolean visible, Badge expectedBadge) {
    BadgesConfiguration badgesConfiguration = createBadges(1);
    ConfiguredProfileBadgeConverter badgeConverter =
        new ConfiguredProfileBadgeConverter(clock, badgesConfiguration, resourceBundleFactory);
    setupResourceBundle(Locale.getDefault());

    if (expectedBadge != null) {
      assertThat(badgeConverter.convert(List.of(), List.of(new AccountBadge(name, expiration, visible)))).isNotNull()
          .hasSize(1)
          .containsOnly(expectedBadge);
    } else {
      assertThat(badgeConverter.convert(List.of(), List.of(new AccountBadge(name, expiration, visible)))).isNotNull()
          .isEmpty();
    }
  }

  @SuppressWarnings("unused")
  static Stream<Arguments> testNoLocales() {
    Instant expired = Instant.ofEpochSecond(41);
    Instant notExpired = Instant.ofEpochSecond(43);
    return Stream.of(
        arguments(idFor(0), expired, false, null),
        arguments(idFor(0), notExpired, false, null),
        arguments(idFor(0), expired, true, null),
        arguments(idFor(0), notExpired, true, new Badge(idFor(0), "other", imageUrlFor(0), nameFor(0), desriptionFor(0))),
        arguments(idFor(1), expired, false, null),
        arguments(idFor(1), notExpired, false, null),
        arguments(idFor(1), expired, true, null),
        arguments(idFor(1), notExpired, true, null)
    );
  }

  @Test
  void testCustomControl() {
    BadgesConfiguration badgesConfiguration = createBadges(1);
    ConfiguredProfileBadgeConverter badgeConverter =
        new ConfiguredProfileBadgeConverter(clock, badgesConfiguration, resourceBundleFactory);

    Locale defaultLocale = Locale.getDefault();
    Locale enGb = new Locale("en", "GB");
    Locale en = new Locale("en");
    Locale esUs = new Locale("es", "US");

    ArgumentCaptor<Control> controlArgumentCaptor = setupResourceBundle(enGb);
    badgeConverter.convert(List.of(enGb, en, esUs),
        List.of(new AccountBadge(idFor(0), Instant.ofEpochSecond(43), true)));
    Control control = controlArgumentCaptor.getValue();

    assertThatNullPointerException().isThrownBy(() -> control.getFormats(null));
    assertThatNullPointerException().isThrownBy(() -> control.getFallbackLocale(null, enGb));
    assertThatNullPointerException().isThrownBy(
        () -> control.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, null));

    assertThat(control.getFormats(ConfiguredProfileBadgeConverter.BASE_NAME)).isNotNull().hasSize(1).containsOnly(
        Control.FORMAT_PROPERTIES.toArray(new String[0]));

    try {
      // temporarily override for purpose of ensuring this test doesn't change based on system default locale
      Locale.setDefault(new Locale("xx", "XX"));

      assertThat(control.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, enGb)).isEqualTo(en);
      assertThat(control.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, en)).isEqualTo(esUs);
      assertThat(control.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, esUs)).isEqualTo(
          Locale.getDefault());
      assertThat(control.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, Locale.getDefault())).isNull();

      // now test what happens if the system default locale is in the list
      // this should always terminate at the system default locale since the development defined bundle should get
      // returned at that point anyhow
      badgeConverter.convert(List.of(enGb, Locale.getDefault(), en, esUs),
          List.of(new AccountBadge(idFor(0), Instant.ofEpochSecond(43), true)));
      Control control2 = controlArgumentCaptor.getValue();

      assertThat(control2.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, enGb)).isEqualTo(
          Locale.getDefault());
      assertThat(control2.getFallbackLocale(ConfiguredProfileBadgeConverter.BASE_NAME, Locale.getDefault())).isNull();
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }
}
