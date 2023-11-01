/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import su.sres.shadowserver.storage.Device.DeviceCapabilities;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static su.sres.shadowserver.util.DevicesHelper.createDevice;
import static su.sres.shadowserver.util.DevicesHelper.setEnabled;

class AccountTest {

    private final Device oldMasterDevice = mock(Device.class);
    private final Device recentMasterDevice = mock(Device.class);
    private final Device agingSecondaryDevice = mock(Device.class);
    private final Device recentSecondaryDevice = mock(Device.class);
    private final Device oldSecondaryDevice = mock(Device.class);

    private final Device gv2CapableDevice = mock(Device.class);
    private final Device gv2IncapableDevice = mock(Device.class);
    private final Device gv2IncapableExpiredDevice = mock(Device.class);

    private final Device gv1MigrationCapableDevice = mock(Device.class);
    private final Device gv1MigrationIncapableDevice = mock(Device.class);
    private final Device gv1MigrationIncapableExpiredDevice = mock(Device.class);
    
    private final Device senderKeyCapableDevice = mock(Device.class);
    private final Device senderKeyIncapableDevice = mock(Device.class);
    private final Device senderKeyIncapableExpiredDevice = mock(Device.class);
    
    private final Device announcementGroupCapableDevice = mock(Device.class);
    private final Device announcementGroupIncapableDevice = mock(Device.class);
    private final Device announcementGroupIncapableExpiredDevice = mock(Device.class);
    
    private final Device changeUserLoginCapableDevice = mock(Device.class);
    private final Device changeUserLoginIncapableDevice = mock(Device.class);
    private final Device changeUserLoginIncapableExpiredDevice = mock(Device.class);

    @BeforeEach
    void setup() {
	when(oldMasterDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(366));
	when(oldMasterDevice.isEnabled()).thenReturn(true);
	when(oldMasterDevice.getId()).thenReturn(Device.MASTER_ID);

	when(recentMasterDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
	when(recentMasterDevice.isEnabled()).thenReturn(true);
	when(recentMasterDevice.getId()).thenReturn(Device.MASTER_ID);

	when(agingSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));
	when(agingSecondaryDevice.isEnabled()).thenReturn(false);
	when(agingSecondaryDevice.getId()).thenReturn(2L);

	when(recentSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
	when(recentSecondaryDevice.isEnabled()).thenReturn(true);
	when(recentSecondaryDevice.getId()).thenReturn(2L);

	when(oldSecondaryDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(366));
	when(oldSecondaryDevice.isEnabled()).thenReturn(false);
	when(oldSecondaryDevice.getId()).thenReturn(2L);

	when(gv2CapableDevice.isGroupsV2Supported()).thenReturn(true);
	when(gv2CapableDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
	when(gv2CapableDevice.isEnabled()).thenReturn(true);

	when(gv2IncapableDevice.isGroupsV2Supported()).thenReturn(false);
	when(gv2IncapableDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
	when(gv2IncapableDevice.isEnabled()).thenReturn(true);

	when(gv2IncapableExpiredDevice.isGroupsV2Supported()).thenReturn(false);
	when(gv2IncapableExpiredDevice.getLastSeen()).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31));
	when(gv2IncapableExpiredDevice.isEnabled()).thenReturn(false);

	when(gv1MigrationCapableDevice.getCapabilities()).thenReturn(
	    new DeviceCapabilities(true, true, true, true, true, true, false, false, false));
	when(gv1MigrationCapableDevice.isEnabled()).thenReturn(true);
	when(gv1MigrationIncapableDevice.getCapabilities()).thenReturn(
	    new DeviceCapabilities(true, true, true, true, true, false, false, false, false));
	when(gv1MigrationIncapableDevice.isEnabled()).thenReturn(true);
	when(gv1MigrationIncapableExpiredDevice.getCapabilities()).thenReturn(
	    new DeviceCapabilities(true, true, true, true, true, false, false, false, false));
	when(gv1MigrationIncapableExpiredDevice.isEnabled()).thenReturn(false);
	
	when(senderKeyCapableDevice.getCapabilities()).thenReturn(
	    new DeviceCapabilities(true, true, true, true, true, true, true, false, false));
    when(senderKeyCapableDevice.isEnabled()).thenReturn(true);

    when(senderKeyIncapableDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, false, false, false));
    when(senderKeyIncapableDevice.isEnabled()).thenReturn(true);

    when(senderKeyIncapableExpiredDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, false, false, false));
    when(senderKeyIncapableExpiredDevice.isEnabled()).thenReturn(false);
    
    when(announcementGroupCapableDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, true, false));
    when(announcementGroupCapableDevice.isEnabled()).thenReturn(true);

    when(announcementGroupIncapableDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, false, false));
    when(announcementGroupIncapableDevice.isEnabled()).thenReturn(true);

    when(announcementGroupIncapableExpiredDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, false, false));
    when(announcementGroupIncapableExpiredDevice.isEnabled()).thenReturn(false);
    
    when(changeUserLoginCapableDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, false, true));
    when(changeUserLoginCapableDevice.isEnabled()).thenReturn(true);

    when(changeUserLoginIncapableDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, false, false));
    when(changeUserLoginIncapableDevice.isEnabled()).thenReturn(true);

    when(changeUserLoginIncapableExpiredDevice.getCapabilities()).thenReturn(
        new DeviceCapabilities(true, true, true, true, true, true, true, false, false));
    when(changeUserLoginIncapableExpiredDevice.isEnabled()).thenReturn(false);
    }

    @Test
    void testIsEnabled() {
	final Device enabledMasterDevice = mock(Device.class);
	final Device enabledLinkedDevice = mock(Device.class);
	final Device disabledMasterDevice = mock(Device.class);
	final Device disabledLinkedDevice = mock(Device.class);

	when(enabledMasterDevice.isEnabled()).thenReturn(true);
	when(enabledLinkedDevice.isEnabled()).thenReturn(true);
	when(disabledMasterDevice.isEnabled()).thenReturn(false);
	when(disabledLinkedDevice.isEnabled()).thenReturn(false);

	when(enabledMasterDevice.getId()).thenReturn(1L);
	when(enabledLinkedDevice.getId()).thenReturn(2L);
	when(disabledMasterDevice.getId()).thenReturn(1L);
	when(disabledLinkedDevice.getId()).thenReturn(2L);

	assertTrue(new Account("+14151234567", UUID.randomUUID(), Set.of(enabledMasterDevice), new byte[0]).isEnabled());
	assertTrue(new Account("+14151234567", UUID.randomUUID(), Set.of(enabledMasterDevice, enabledLinkedDevice), new byte[0]).isEnabled());
	assertTrue(new Account("+14151234567", UUID.randomUUID(), Set.of(enabledMasterDevice, disabledLinkedDevice), new byte[0]).isEnabled());
	assertFalse(new Account("+14151234567", UUID.randomUUID(), Set.of(disabledMasterDevice), new byte[0]).isEnabled());
	assertFalse(new Account("+14151234567", UUID.randomUUID(), Set.of(disabledMasterDevice, enabledLinkedDevice), new byte[0]).isEnabled());
	assertFalse(new Account("+14151234567", UUID.randomUUID(), Set.of(disabledMasterDevice, disabledLinkedDevice), new byte[0]).isEnabled());
    }

    @Test
    void testCapabilities() {
	Account uuidCapable = new Account("+14152222222", UUID.randomUUID(), new HashSet<Device>() {
	    {
		add(gv2CapableDevice);
	    }
	}, "1234".getBytes());

	Account uuidIncapable = new Account("+14152222222", UUID.randomUUID(), new HashSet<Device>() {
	    {
		add(gv2CapableDevice);
		add(gv2IncapableDevice);
	    }
	}, "1234".getBytes());

	Account uuidCapableWithExpiredIncapable = new Account("+14152222222", UUID.randomUUID(), new HashSet<Device>() {
	    {
		add(gv2CapableDevice);
		add(gv2IncapableExpiredDevice);
	    }
	}, "1234".getBytes());

	assertTrue(uuidCapable.isGroupsV2Supported());
	assertFalse(uuidIncapable.isGroupsV2Supported());
	assertTrue(uuidCapableWithExpiredIncapable.isGroupsV2Supported());
    }

    @Test
    void testIsTransferSupported() {
	final Device transferCapableMasterDevice = mock(Device.class);
	final Device nonTransferCapableMasterDevice = mock(Device.class);
	final Device transferCapableLinkedDevice = mock(Device.class);

	final DeviceCapabilities transferCapabilities           = mock(DeviceCapabilities.class);
    final DeviceCapabilities nonTransferCapabilities        = mock(DeviceCapabilities.class);

	when(transferCapableMasterDevice.getId()).thenReturn(1L);
	when(transferCapableMasterDevice.isMaster()).thenReturn(true);
	when(transferCapableMasterDevice.getCapabilities()).thenReturn(transferCapabilities);

	when(nonTransferCapableMasterDevice.getId()).thenReturn(1L);
	when(nonTransferCapableMasterDevice.isMaster()).thenReturn(true);
	when(nonTransferCapableMasterDevice.getCapabilities()).thenReturn(nonTransferCapabilities);

	when(transferCapableLinkedDevice.getId()).thenReturn(2L);
	when(transferCapableLinkedDevice.isMaster()).thenReturn(false);
	when(transferCapableLinkedDevice.getCapabilities()).thenReturn(transferCapabilities);

	when(transferCapabilities.isTransfer()).thenReturn(true);
	when(nonTransferCapabilities.isTransfer()).thenReturn(false);

	{
	    final Account transferableMasterAccount = new Account("+14152222222", UUID.randomUUID(), Collections.singleton(transferCapableMasterDevice), "1234".getBytes());

	    assertTrue(transferableMasterAccount.isTransferSupported());
	}

	{
	    final Account nonTransferableMasterAccount = new Account("+14152222222", UUID.randomUUID(), Collections.singleton(nonTransferCapableMasterDevice), "1234".getBytes());

	    assertFalse(nonTransferableMasterAccount.isTransferSupported());
	}

	{
	    final Account transferableLinkedAccount = new Account("+14152222222", UUID.randomUUID(), new HashSet<>() {
		{
		    add(nonTransferCapableMasterDevice);
		    add(transferCapableLinkedDevice);
		}
	    }, "1234".getBytes());

	    assertFalse(transferableLinkedAccount.isTransferSupported());
	}
    }

    @Test
    void testDiscoverableByUserLogin() {
	final Account account = new Account("+14152222222", UUID.randomUUID(), Collections.singleton(recentMasterDevice), "1234".getBytes());

    assertTrue(account.isDiscoverableByUserLogin(),
        "Freshly-loaded legacy accounts should be discoverable by user login.");

	account.setDiscoverableByUserLogin(false);
	assertFalse(account.isDiscoverableByUserLogin());

	account.setDiscoverableByUserLogin(true);
	assertTrue(account.isDiscoverableByUserLogin());
    }

    @Test
    void isGroupsV2Supported() {
	assertTrue(new Account("+18005551234", UUID.randomUUID(), Set.of(gv2CapableDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGroupsV2Supported());
	assertTrue(new Account("+18005551234", UUID.randomUUID(), Set.of(gv2CapableDevice, gv2IncapableExpiredDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGroupsV2Supported());
	assertFalse(new Account("+18005551234", UUID.randomUUID(), Set.of(gv2CapableDevice, gv2IncapableDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGroupsV2Supported());
    }

    @Test
    void isGv1MigrationSupported() {
	assertTrue(new Account("+18005551234", UUID.randomUUID(), Set.of(gv1MigrationCapableDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGv1MigrationSupported());
	assertFalse(new Account("+18005551234", UUID.randomUUID(), Set.of(gv1MigrationCapableDevice, gv1MigrationIncapableDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGv1MigrationSupported());
	assertTrue(new Account("+18005551234", UUID.randomUUID(), Set.of(gv1MigrationCapableDevice, gv1MigrationIncapableExpiredDevice), "1234".getBytes(StandardCharsets.UTF_8)).isGv1MigrationSupported());
    }
    
    @Test
    void isSenderKeySupported() {
      assertThat(new Account("+18005551234", UUID.randomUUID(), Set.of(senderKeyCapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isSenderKeySupported()).isTrue();
      assertThat(new Account("+18005551234", UUID.randomUUID(), Set.of(senderKeyCapableDevice, senderKeyIncapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isSenderKeySupported()).isFalse();
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(senderKeyCapableDevice, senderKeyIncapableExpiredDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isSenderKeySupported()).isTrue();
    }
    
    @Test
    void isAnnouncementGroupSupported() {
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(announcementGroupCapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isAnnouncementGroupSupported()).isTrue();
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(announcementGroupCapableDevice, announcementGroupIncapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isAnnouncementGroupSupported()).isFalse();
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(announcementGroupCapableDevice, announcementGroupIncapableExpiredDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isAnnouncementGroupSupported()).isTrue();
    }
    
    @Test
    void isChangeUserLoginSupported() {
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(changeUserLoginCapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isChangeUserLoginSupported()).isTrue();
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(changeUserLoginCapableDevice, changeUserLoginIncapableDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isChangeUserLoginSupported()).isFalse();
      assertThat(new Account("+18005551234", UUID.randomUUID(),
          Set.of(changeUserLoginCapableDevice, changeUserLoginIncapableExpiredDevice),
          "1234".getBytes(StandardCharsets.UTF_8)).isChangeUserLoginSupported()).isTrue();
    }
    
    @Test
    void getNextDeviceId() {

      final Set<Device> devices = new HashSet<>();
      devices.add(createDevice(Device.MASTER_ID));

      final Account account = new Account("+14151234567", UUID.randomUUID(), devices, new byte[0]);

      assertThat(account.getNextDeviceId()).isEqualTo(2L);

      account.addDevice(createDevice(2L));

      assertThat(account.getNextDeviceId()).isEqualTo(3L);

      account.addDevice(createDevice(3L));

      setEnabled(account.getDevice(2L).orElseThrow(), false);

      assertThat(account.getNextDeviceId()).isEqualTo(4L);

      account.removeDevice(2L);

      assertThat(account.getNextDeviceId()).isEqualTo(2L);
    }
    
    @Test
    void addAndRemoveBadges() {
      final Account account = new Account("+14151234567", UUID.randomUUID(), Set.of(createDevice(Device.MASTER_ID)), new byte[0]);
      final Clock clock = mock(Clock.class);
      when(clock.instant()).thenReturn(Instant.ofEpochSecond(40));

      account.addBadge(clock, new AccountBadge("foo", Instant.ofEpochSecond(42), false));
      account.addBadge(clock, new AccountBadge("bar", Instant.ofEpochSecond(44), true));
      account.addBadge(clock, new AccountBadge("baz", Instant.ofEpochSecond(46), true));

      assertThat(account.getBadges()).hasSize(3);

      account.removeBadge(clock, "baz");

      assertThat(account.getBadges()).hasSize(2);

      account.addBadge(clock, new AccountBadge("foo", Instant.ofEpochSecond(50), false));

      assertThat(account.getBadges()).hasSize(2).element(0).satisfies(badge -> {
        assertThat(badge.getId()).isEqualTo("foo");
        assertThat(badge.getExpiration().getEpochSecond()).isEqualTo(50);
        assertThat(badge.isVisible()).isFalse();
      });

      account.addBadge(clock, new AccountBadge("foo", Instant.ofEpochSecond(51), true));

      assertThat(account.getBadges()).hasSize(2).element(0).satisfies(badge -> {
        assertThat(badge.getId()).isEqualTo("foo");
        assertThat(badge.getExpiration().getEpochSecond()).isEqualTo(51);
        assertThat(badge.isVisible()).isTrue();
      });
    }
}