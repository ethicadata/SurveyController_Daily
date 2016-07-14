# [Ethica](https://www.ethicadata.com/) Daily Survey Controller

This is a context analyzer for Ethica which releases surveys n times a day, where the prompts are uniformly distributed throughout the day. There are a few points included in this logic to ensure prompts are properly distributed throughout the day:

1. To make sure there is no multiple prompts at on part of the day and none at other times, this context analyzer divides the day into multiple blocks of equal length, where participants will receive exactly one survey at each block.
2. The blocks are not covering 24 hours of the day, as then participants would receive surveys at inconvenient times, e.g. passed night. Rather the duration between start and end hour of the day are divided into blocks.
3. When participant receives a prompt, they will be given a certain amount of time to answer the surveys, e.g. 30 minutes or 1 hour. To ensure no two consecutive survey are piled up during that time, you can configure each two consecutive prompts to be apart for certain duration at the minimum.

## Build
This project is configured to build using Apache ANT. It requires you to have [Android SDK Build Tools](https://developer.android.com/studio/install.html) installed in your system. Assuming you have it installed, first run the following command to setup your environment:

```
android update project -p .
```

Then build the project:

```
ant clean release
```

This will generate a JAR file at bin/DailySurveyController.jar (or any other name, if you have changed the name), which you can use in Ethica as your context analyzer.
