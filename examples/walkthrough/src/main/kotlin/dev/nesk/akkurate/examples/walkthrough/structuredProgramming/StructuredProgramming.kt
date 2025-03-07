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

package dev.nesk.akkurate.examples.walkthrough.structuredProgramming

import dev.nesk.akkurate.Validator
import dev.nesk.akkurate.accessors.each
import dev.nesk.akkurate.accessors.iterator
import dev.nesk.akkurate.annotations.Validate
import dev.nesk.akkurate.constraints.builders.hasSizeLowerThanOrEqualTo
import dev.nesk.akkurate.constraints.builders.isNotEmpty
import dev.nesk.akkurate.examples.walkthrough.structuredProgramming.validation.accessors.books
import dev.nesk.akkurate.examples.walkthrough.structuredProgramming.validation.accessors.maximumCapacity
import dev.nesk.akkurate.examples.walkthrough.structuredProgramming.validation.accessors.title

// Inside your validator, you can write your code just like you would outside of it.
// You need to use conditions? Loops? To reuse some data of your payload to define
// some constraints? We've got you covered!

// Let's showcase this with a `Library` which can contain a limited amount of books.

@Validate
data class Book(val title: String)

@Validate
data class Library(val books: Set<Book>, val maximumCapacity: Int)

val validateLibrary = Validator<Library> {

    // You can use a loops to iterate over collections.
    for (book in books) {
        book.title.isNotEmpty()
    }

    // However, since iterating over a collection to validate is common, you might as well use the following helper.
    books.each {
        title.isNotEmpty()
    }

    // We want to constrain the size of the book collection only if the maximum capacity is at least 1.
    // So we unwrap the value, add a condition, and create a constraint only if its positive.
    val (max) = maximumCapacity
    if (max > 0) {
        books.hasSizeLowerThanOrEqualTo(max)
    }

}
