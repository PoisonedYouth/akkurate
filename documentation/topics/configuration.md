# Configuration

Validators are instantiated with a default configuration. You can override some values to your needs by providing
a `Configuration` when instantiating your validator, using `Validator<T>(CONFIGURATION_INSTANCE) { ... }`.

Here's an example where we override the default violation message and the root path:

```kotlin
val customConfig = Configuration(
    defaultViolationMessage = "WRONG",
    rootPath = emptyList("custom", "root", "path"),
)

val validate = Validator<String>(customConfig) {
    length.constrain { false }
}

when (val result = validate("foo")) {
    is ValidationResult.Success -> {}

    is ValidationResult.Failure -> {
        for ((message, path) in result.violations) {
            println("$path: $message")
        }
    }
}
```

The output is the following:

```text
[custom, root, path, length]: WRONG
```

Without providing any custom configuration, it would have been:

```text
[length]: The value is invalid.
```
