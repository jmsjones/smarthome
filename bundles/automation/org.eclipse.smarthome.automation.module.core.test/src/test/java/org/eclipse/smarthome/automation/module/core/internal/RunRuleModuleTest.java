/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.automation.module.core.internal;

import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.smarthome.automation.Action;
import org.eclipse.smarthome.automation.Rule;
import org.eclipse.smarthome.automation.RuleRegistry;
import org.eclipse.smarthome.automation.RuleStatus;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.common.registry.ProviderChangeListener;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.events.EventFilter;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.events.EventSubscriber;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemNotFoundException;
import org.eclipse.smarthome.core.items.ItemProvider;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.events.ItemCommandEvent;
import org.eclipse.smarthome.core.items.events.ItemEventFactory;
import org.eclipse.smarthome.core.library.items.SwitchItem;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.test.java.JavaOSGiTest;
import org.eclipse.smarthome.test.storage.VolatileStorageService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This tests the RunRuleAction
 *
 * @author Benedikt Niehues - initial contribution
 * @author Markus Rathgeb - Migrated Groovy tests to pure Java ones and made it more robust
 */
public class RunRuleModuleTest extends JavaOSGiTest {

    private final Logger logger = LoggerFactory.getLogger(RunRuleModuleTest.class);
    private final VolatileStorageService volatileStorageService = new VolatileStorageService();

    @Before
    public void before() {
        registerService(new ItemProvider() {
            @Override
            public void addProviderChangeListener(final ProviderChangeListener<Item> listener) {
            }

            @Override
            public void removeProviderChangeListener(final ProviderChangeListener<Item> listener) {
            }

            @Override
            public Collection<Item> getAll() {
                return Arrays.asList(new Item[] { new SwitchItem("switch1"), new SwitchItem("switch2"),
                        new SwitchItem("switch3"), new SwitchItem("ruleTrigger") });
            }
        });
        registerService(volatileStorageService);
    }

    private Rule createSceneRule() {
        final Configuration sceneRuleAction1Config = new Configuration(Collections
                .unmodifiableMap(Stream.of(new SimpleEntry<>("itemName", "switch1"), new SimpleEntry<>("command", "ON"))
                        .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()))));
        final Configuration sceneRuleAction2Config = new Configuration(Collections
                .unmodifiableMap(Stream.of(new SimpleEntry<>("itemName", "switch2"), new SimpleEntry<>("command", "ON"))
                        .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()))));
        final Configuration sceneRuleAction3Config = new Configuration(Collections
                .unmodifiableMap(Stream.of(new SimpleEntry<>("itemName", "switch3"), new SimpleEntry<>("command", "ON"))
                        .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()))));

        final Rule sceneRule = new Rule("exampleSceneRule");

        sceneRule.setActions(Arrays.asList(new Action[] {
                new Action("sceneItemPostCommandAction1", "core.ItemCommandAction", sceneRuleAction1Config, null),
                new Action("sceneItemPostCommandAction2", "core.ItemCommandAction", sceneRuleAction2Config, null),
                new Action("sceneItemPostCommandAction3", "core.ItemCommandAction", sceneRuleAction3Config, null) }));

        sceneRule.setName("Example Scene");

        return sceneRule;
    }

    private Rule createOuterRule() {
        final Configuration outerRuleTriggerConfig = new Configuration(Collections.unmodifiableMap(Stream
                .of(new SimpleEntry<>("eventSource", "ruleTrigger"), new SimpleEntry<>("eventTopic", "smarthome/*"),
                        new SimpleEntry<>("eventTypes", "ItemStateEvent"))
                .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()))));

        final Configuration outerRuleActionConfig = new Configuration(
                Collections.unmodifiableMap(Stream.of(new SimpleEntry<>("ruleUIDs", "[exampleSceneRule]"))
                        .collect(Collectors.toMap((e) -> e.getKey(), (e) -> e.getValue()))));

        final Rule outerRule = new Rule("sceneActivationRule");

        outerRule.setTriggers(Arrays.asList(new Trigger[] {
                new Trigger("ItemStateChangeTrigger2", "core.GenericEventTrigger", outerRuleTriggerConfig) }));

        outerRule.setActions(Arrays.asList(
                new Action[] { new Action("RunRuleAction1", "core.RunRuleAction", outerRuleActionConfig, null) }));

        outerRule.setName("scene activator");

        return outerRule;
    }

    @Test
    public void sceneActivatedByRule() throws ItemNotFoundException, InterruptedException {
        final RuleRegistry ruleRegistry = getService(RuleRegistry.class);
        Assert.assertNotNull(ruleRegistry);

        // Scene rule

        final Rule sceneRule = createSceneRule();
        logger.info("SceneRule created: {}", sceneRule.getUID());

        ruleRegistry.add(sceneRule);
        ruleRegistry.setEnabled(sceneRule.getUID(), true);
        waitForAssert(() -> {
            Assert.assertEquals(RuleStatus.IDLE, ruleRegistry.getStatusInfo(sceneRule.getUID()).getStatus());
        });

        // Outer rule

        final Rule outerRule = createOuterRule();
        logger.info("SceneActivationRule created: {}", outerRule.getUID());

        ruleRegistry.add(outerRule);
        ruleRegistry.setEnabled(outerRule.getUID(), true);
        waitForAssert(() -> {
            Assert.assertEquals(RuleStatus.IDLE, ruleRegistry.getStatusInfo(outerRule.getUID()).getStatus());
        });

        // Test rule

        final EventPublisher eventPublisher = getService(EventPublisher.class);
        Assert.assertNotNull(eventPublisher);

        final ItemRegistry itemRegistry = getService(ItemRegistry.class);
        Assert.assertNotNull(itemRegistry);

        final Queue<Event> events = new LinkedList<>();

        registerService(new EventSubscriber() {
            @Override
            public void receive(final Event event) {
                logger.info("Event: {}", event.getTopic());
                events.add(event);
            }

            @Override
            public Set<String> getSubscribedEventTypes() {
                return Collections.singleton(ItemCommandEvent.TYPE);
            }

            @Override
            public EventFilter getEventFilter() {
                return null;
            }
        });

        // trigger rule by switching triggerItem ON
        eventPublisher.post(ItemEventFactory.createStateEvent("ruleTrigger", OnOffType.ON));
        waitForAssert(() -> {
            assertFalse(events.isEmpty());
            ItemCommandEvent event = (ItemCommandEvent) events.remove();
            assertEquals("smarthome/items/switch3/command", event.getTopic());
            assertEquals(OnOffType.ON, event.getItemCommand());
        });

    }
}
