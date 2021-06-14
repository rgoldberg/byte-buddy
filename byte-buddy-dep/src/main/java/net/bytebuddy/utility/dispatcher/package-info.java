/*
 * Copyright 2014 - Present Rafael Winterhalter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * A package to handle dispatching of classes. This package is intentionally not exported
 * to avoid exposing this mechanism to external users when Byte Buddy is used as a module.
 * This way, external users cannot emulate Byte Buddy's privilege when caller sensitive code
 * would be proxied.
 */
package net.bytebuddy.utility.dispatcher;
