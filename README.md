# Device Admin Demo

Демонстрационное Android приложение показывающее возможности **Device Admin API**.

## Что умеет

- ✅ Активация / деактивация прав администратора устройства
- ✅ Мгновенная блокировка экрана (`lockNow()`)
- ✅ Системное отключение камеры (`setCameraDisabled()`)
- ✅ Защита от удаления пока активен Admin
- ✅ Перехват событий: неверный пароль, попытки разблокировки

## Структура проекта

```
app/src/main/
├── AndroidManifest.xml          # Регистрация AdminReceiver
├── java/com/example/
│   ├── MainActivity.java        # UI и логика управления
│   └── AdminReceiver.java       # Обработчик событий Device Admin
└── res/
    ├── xml/device_admin_policies.xml  # Список политик
    ├── layout/activity_main.xml       # Интерфейс
    └── values/                        # Строки и стили
```

## Как собрать

### Через Android Studio
1. Открыть папку проекта
2. `Build → Make Project`
3. `Run → Run 'app'`

### Через командную строку
```bash
./gradlew assembleDebug
# APK будет в: app/build/outputs/apk/debug/
```

## Как работает Device Admin

```
Пользователь нажимает "Активировать"
        ↓
Система показывает диалог с列списком политик
        ↓
Пользователь подтверждает
        ↓
AdminReceiver.onEnabled() вызывается
        ↓
Приложение получает права администратора:
- Нельзя удалить обычным способом
- Доступны системные функции (блокировка, камера и т.д.)
```

## Требования

- Android 8.0+ (API 26+)
- Протестировано на MIUI 13 (Xiaomi)

## Следующие шаги

- [ ] VpnService — мониторинг сетевого трафика
- [ ] AppOpsManager — мониторинг микрофона/камеры
- [ ] UsageStatsManager — статистика приложений
- [ ] Детектор слежки

## Важно

Этот проект создан в образовательных целях для изучения Android Device Admin API.
