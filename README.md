# AI Friend

AI Friend is a Minecraft plugin that enhances player interactions by providing an AI chatbot that responds to player messages. The plugin uses OpenAI's API to generate responses based on player chat history.

## Features

- **AI Chatbot**: An AI-powered chatbot that interacts with players in real-time.
- **Customizable**: Configure the chatbot's name, preferred language, and custom database.
- **Scheduled API Calls**: Periodically calls the OpenAI API to generate responses.
- **Command Support**: Provides a command to display the current configuration.
- **Player angryness recognition**: The AI chatbot can recognize if the player is angry and respond accordingly. Stopping the responses (API calls) for 30 minutes.
- **Personalization**: You can add your own custom message to the AI chatbot.
- **Free Trial**: The plugin offers the "trial" API key to use for 3 days.

## Commands

- **/aifriend**: Shows AI Friend configuration information. Only accessible by server operators.

## Configuration (`config.yml`)

The `config.yml` file allows you to customize the behavior of the AI Friend plugin. Below is a description of each configuration option:

```yaml
openai_api_key: "your_openai_api_key_here"  # Your OpenAI API key.
enabled: true  # Enable or disable the AI Friend plugin.
timer_interval: 60  # Interval in seconds for calling the OpenAI API.
openai_chatbot_name: "AiFriend"  # Name of the AI chatbot.
preffered_language: "English"  # Preferred language for the chatbot.
personalization: ""  # Custom database - optional. (your custom message for the AI chatbot)
 ```

## Installation
1. Download the AI Friend plugin jar file.
2. Place the jar file in your server's plugins directory.
3. Start your Minecraft server to generate the default \`config.yml\` file.
4. Edit the \`config.yml\` file to include your OpenAI API key and other desired settings.
5. Restart your server to apply the changes.

## Usage
Once the plugin is installed and configured, the AI chatbot will automatically start responding to player messages based on the configured interval. Server operators can use the \`/aifriend\` command to view the current configuration.

## Authors - Support
- Filasik - Discord ID: filasik
- Patr1xCZ - Discord ID: paprik_man