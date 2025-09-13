# Example IntelliJ IDEA Plugin Project

Этот проект представляет собой тестовый плагин для IntelliJ IDEA. 
Он добавляет дополнительные функции и интеграции в IDE:
- генерацию автотестов с помощью GigaChat
- реализацию VirtualFile для отображения информации по ручным тестам

---

## Сборка и Запуск

### Требования

- [Java Development Kit (JDK) 21+](https://adoptium.net/)
- [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) (для разработки)
- [Gradle](https://gradle.org/) (встроенный в IDEA)
- [Ключ для работы с GigaChat API](https://developers.sber.ru/docs/ru/gigachat/individuals-quickstart). Ключ необходимо положить в `resources/plugin.properties`


### Шаги по сборке

1. **Откройте проект в IntelliJ IDEA**:
    - Откройте файл `build.gradle` или папку проекта.
    - Убедитесь, что выбран JDK 21+.

2. **Запустите задачу сборки**:
    - В терминале выполните команду:
      ```bash
      ./gradlew buildPlugin
      ```
    - Плагин будет собран в директории:
      ```
      build/distributions/
      ```

3. **Тестирование плагина**:
    - Используйте задачу `runIde`:
      ```bash
      ./gradlew runIde
      ```
    - Это запустит новый инстанс IDEA с вашим плагином.
   Через новый инстанс можно выбрать ваш проект и проверять работу плагина 

---

## Установка плагина

### Вручную

1. Соберите плагин командой:
   ```bash
   ./gradlew buildPlugin
   ```

2. Получите `.zip` файл из:
   ```
   build/distributions/
   ```

3. Установите его в IntelliJ IDEA:
    - `File > Settings > Plugins > Install Plugin from Disk...`
    - Выберите собранный `.zip` файл.

### Через репозиторий

Если вы опубликовали плагин в репозитории (например, [JetBrains Plugin Repository](https://plugins.jetbrains.com/)):

1. Перейдите в `Settings > Plugins`.
2. Найдите плагин по имени.
3. Нажмите "Install".

---

## Документация

- Официальная документация по разработке плагинов:  
  [https://plugins.jetbrains.com/docs/intellij/welcome.html](https://plugins.jetbrains.com/docs/intellij/welcome.html)

- Полезное при работе с UI
  [https://plugins.jetbrains.com/docs/intellij/internal-ui-inspector.html](https://plugins.jetbrains.com/docs/intellij/internal-ui-inspector.html)

- Исходный код IDEA (полезно когда нужно найти реализацию какого-то функционала):  
  [https://github.com/JetBrains/intellij-community](https://github.com/JetBrains/intellij-community)

- Справка по Gradle для плагинов:  
  [https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)

- Про Swing:
- [https://java-online.ru/libs-swing.xhtml](https://java-online.ru/libs-swing.xhtml)

- Больше примеров:
  [https://github.com/JetBrains/intellij-sdk-code-samples](https://github.com/JetBrains/intellij-sdk-code-samples)
---

## Тестирование

Для тестирования можно использовать встроенные инструменты IDEA:
- `runIde`: запуск тестовой версии IDE с вашим плагином.
- `test`: запуск unit-тестов.

---