# Spring Initializr TUI

A terminal user interface for [Spring Initializr](https://start.spring.io).

Spring Initializr TUI lets you create and configure Spring Boot projects directly from your terminal without opening a browser.

The application uses the Spring Initializr API to retrieve the available project metadata, including Spring Boot versions, Java versions, project types, and dependencies and after generating automatically unzips the project in the cwd.

<img width="1256" height="1357" alt="image" src="https://github.com/user-attachments/assets/c469a103-79e9-4ebd-b5c6-81a16f8b98f8" />
<img width="1244" height="1360" alt="image" src="https://github.com/user-attachments/assets/f9f36c4b-ceda-45e2-bfe0-6484aa3b5d58" />

## Why?

[Spring Initializr](https://start.spring.io) is a great way to bootstrap a Spring Boot project, but it requires switching to a browser or using the Spring Boot CLI.

This project provides a similar workflow directly in the terminal:

The goal is to make creating a new Spring Boot project as simple as:

```bash
spring-initializr
```

## Requirements

* Java 21+
* A terminal with ANSI/TTY support
* Internet connection

The application communicates with `start.spring.io` to retrieve metadata and generate projects.

## Installation

### Build from source

Clone the repository:

```bash
git clone https://github.com/simonhareter/spring-initializr-tui.git
cd spring-initializr-tui
```

To create an installable distribution:

```bash
./gradlew installDist
```

The generated application can then be found in:

```text
app/build/install/spring-initializr/
```

Run it with:

```bash
./app/build/install/spring-initializr/bin/spring-initializr
```

### Add to PATH

To run the application from anywhere, add the generated `spring-initializr` executable inside the `bin` directory to your `PATH`.

For example, with bash:

```bash
# Add to ~/.bashrc
alias spring-initializr=/path/to/spring-initializr/app/build/install/spring-initializr/bin/spring-initializr
```

You can then run:

```bash
spring-initializr
```

## Roadmap

This project is still under development.

Potential improvements include:

* [ ] Add the dependency filter feature
* [ ] Improve the dependency selection experience
* [ ] Improve error handling
* [ ] Add more tests
* [ ] Provide pre-built releases
* [ ] Improve cross-platform support

## Contributing

Contributions, issues, and suggestions are welcome.

If you find a bug or have an idea for improving the TUI, feel free to open an issue or submit a pull request.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for more information.
