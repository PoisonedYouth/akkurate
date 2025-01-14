/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nesk.akkurate.examples.walkthrough.conditionalConstraint

import dev.nesk.akkurate.ValidationResult
import dev.nesk.akkurate.Validator
import dev.nesk.akkurate.annotations.Validate
import dev.nesk.akkurate.constraints.builders.hasLengthGreaterThanOrEqualTo
import dev.nesk.akkurate.constraints.constrain
import dev.nesk.akkurate.constraints.otherwise
import dev.nesk.akkurate.examples.walkthrough.conditionalConstraint.validation.accessors.handle

// Let's take the case of Twitter. When it launched, you could register with a one-character
// handle. However, this is no longer possible, you must use at least five characters.
//
// Now imagine being Twitter's product owner for today's registration page. Someone tries to
// register with the "n" handle. This is less than five characters, and it already exists.
// So, what errors do you want to display?
//
//   — Two errors? One about character count, and another one about an already taken handle?
//   — Only one error about character count?
//
// Since new users cannot register with handles shorter than 5 characters anyway, you might want
// to skip the database check when the handle is too short.
//
// This is a typical case for conditional constraints.

@Validate
data class TwitterRegistration(val handle: String)

class UserRepository {
    fun existsByHandle(handle: String): Boolean = true // fake implementation
}

val validateTwitterRegistration = Validator<UserRepository, TwitterRegistration> { repository ->

    // When applying a constraint, you can use the `satisfied` property to check if the constraint
    // is satisfied or not. Or you can also use destructuring, which is easier to use.
    val (isValidHandle) = handle.hasLengthGreaterThanOrEqualTo(5)

    // Now you can apply other constraints based on the result of a previous one. Here, the database
    // constraint will be applied only if the handle is at least five characters.
    if (isValidHandle) {
        handle.constrain { !repository.existsByHandle(it) } otherwise { "This handle is already taken" }
    }

}

fun main() {

    val validateTwitterRegistrationWithContext = validateTwitterRegistration(UserRepository())

    // Failure, prints the following lines:
    //   handle: Must contain at least 5 characters
    val tooShort = TwitterRegistration("n")
    val tooShortResult = validateTwitterRegistrationWithContext(tooShort)
    if (tooShortResult is ValidationResult.Failure) {
        for (violation in tooShortResult.violations) {
            println("${violation.path.joinToString(".")}: ${violation.message}")
        }
    }

    // Failure, prints the following lines:
    //   handle: This handle is already taken
    val alreadyTaken = TwitterRegistration("steve")
    val alreadyTakenResult = validateTwitterRegistrationWithContext(alreadyTaken)
    if (alreadyTakenResult is ValidationResult.Failure) {
        for (violation in alreadyTakenResult.violations) {
            println("${violation.path.joinToString(".")}: ${violation.message}")
        }
    }
}
