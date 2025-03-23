# AI Anki Helper

AI Anki Helper is an Android application designed to assist users in creating and managing Anki flashcards with the help of AI. The app integrates with AnkiDroid and leverages OpenAI's GPT models to generate high-quality flashcards from user input.

## Features
- **AI-Powered Flashcard Generation**: Automatically generates flashcards using GPT models.
- **AnkiDroid Integration**: Seamlessly integrates with AnkiDroid for flashcard management.
- **Customizable Prompts**: Allows users to manage and customize AI prompts for flashcard generation.
- **Dark Mode Support**: Supports system-wide dark mode for better user experience.
- **Network Security**: Configurable network security settings for secure API communication.
- **Multi-Language Support**: Supports both English and Chinese interfaces.

## Prerequisites
- **Android Studio**: Version 2022.3.1 or later.
- **Java/Kotlin**: Java 8 or Kotlin 1.8.
- **AnkiDroid**: Installed on the device for flashcard management.
- **OpenAI API Key**: Required for accessing GPT models.

## Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/ai-anki-helper.git
   ```
2. Open the project in Android Studio.
3. Sync the project with Gradle files.
4. Build and run the app on an emulator or physical device.

## Project Structure
/app/src/main
├── java/ # Java/Kotlin source files
│ └── com/ss/aianki/ # Main package
├── res/ # Resources (layouts, drawables, strings, etc.)
└── AndroidManifest.xml # App manifest file


## Usage
1. **Set Up OpenAI API Key**:
   - Navigate to the settings menu and enter your OpenAI API key.
2. **Generate Flashcards**:
   - Input your text or questions, and the app will generate flashcards using AI.
3. **Manage Prompts**:
   - Customize and manage AI prompts for different types of flashcards.
4. **Sync with AnkiDroid**:
   - Export generated flashcards directly to AnkiDroid for review.

## Contributing
Contributions are welcome! Please follow these steps:
1. Fork the repository.
2. Create a new branch (`git checkout -b feature/YourFeature`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature/YourFeature`).
5. Open a pull request.

## License
This project is licensed under the [MIT License](LICENSE).

## Acknowledgments
- **OpenAI**: For providing the GPT models used in this app.
- **AnkiDroid**: For the integration and flashcard management.
- **Retrofit**: For network requests and API communication.
- **Material Design**: For the UI components and design guidelines.

## Contact
For questions or feedback, reach out to [hhsw](mailto:qiuq.chou@gmail.com).