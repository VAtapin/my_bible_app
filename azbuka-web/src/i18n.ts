import { computed, ref } from 'vue'
import type { Locale } from '@/domain/types'

const messages = {
  ru: {
    appName: 'Азбука с цифирью',
    appSubtitle: 'Церковнославянский тренажёр',
    home: 'Главная', learn: 'Азбука', practice: 'Тренировка', profile: 'Профиль',
    welcome: 'Учитесь читать знаки, а не угадывать их',
    welcomeText: 'Короткие занятия знакомят с названиями букв, их смыслом и числовыми значениями.',
    continue: 'Продолжить обучение', startPractice: 'Начать тренировку',
    today: 'Сегодня', dailyGoal: 'Цель на день', questions: 'вопросов',
    slavonicClock: 'Церковнославянские часы', clockIntro: 'Текущее время, записанное буквенной цифирью.',
    clockReference: 'обычная запись', clockZeroNote: '— означает ноль: отдельного знака для него в цифири нет.',
    alphabet: 'Церковнославянская азбука', alphabetIntro: 'Нажмите на букву, чтобы открыть карточку.',
    learned: 'изучено', back: 'Назад', letterName: 'Название', meaning: 'Смысл имени',
    transliteration: 'Чтение', number: 'Числовое значение', noNumber: 'не используется',
    markLearned: 'Отметить изученной', learnedDone: 'Буква изучена', nextLetter: 'Следующая буква',
    practiceTitle: 'Тренировка', practiceIntro: '10 заданий без таймера. Ошибки можно спокойно разобрать.',
    namesMode: 'Буквы и имена', numbersMode: 'Цифирь включена', start: 'Начать',
    chooseName: 'Как называется эта буква?', chooseGlyph: 'Выберите букву', chooseNumber: 'Каково числовое значение?',
    correct: 'Верно!', great: 'Отличная серия!', excellent: 'Блестяще!', corrected: 'Теперь верно',
    wrong: 'Не совсем', tapCorrect: 'Правильный ответ подсвечен', chooseHighlighted: 'Нажмите правильный вариант',
    correctedHint: 'Ответ закреплён', autoNext: 'следующее задание автоматически',
    streakLabel: 'Серия', pointsLabel: 'очков', bestStreak: 'лучшая серия',
    next: 'Дальше', finish: 'Завершить',
    resultTitle: 'Занятие завершено', resultText: 'Правильных ответов', retry: 'Ещё раз',
    settings: 'Настройки', interfaceLanguage: 'Язык интерфейса', russian: 'Русский', german: 'Deutsch',
    goal: 'Дневная цель', progress: 'Общий прогресс', accuracy: 'Точность', streak: 'Дней подряд',
    reset: 'Сбросить учебный прогресс', resetConfirm: 'Нажмите ещё раз для подтверждения',
    onboardingTitle: 'Начнём с удобного языка', onboardingText: 'Его можно изменить в профиле в любое время.',
    begin: 'Начать обучение', install: 'Можно установить', installText: 'Но это необязательно — приложение полностью работает в браузере.',
    offline: 'Доступно без сети после первого открытия', loading: 'Загружаем ваш прогресс…',
    audioPreview: 'Прослушать название', audioExample: 'Прослушать пример',
    exampleWithLetter: 'Пример с буквой', readInContext: 'Буква в живом тексте',
    exampleCorpus: 'Источник текста: Библия церковнославянская, 1900 · корпус Bible Desktop',
    systemVoiceNotice: 'Пока используется системный голос; собственная запись диктора готовится.',
    speechUnavailable: 'Озвучивание недоступно в этом браузере',
    mastered: 'Изучено букв', totalAnswers: 'Всего ответов'
  },
  de: {
    appName: 'Alphabet und Zahlen',
    appSubtitle: 'Kirchenslawischer Trainer',
    home: 'Start', learn: 'Alphabet', practice: 'Übung', profile: 'Profil',
    welcome: 'Zeichen lesen lernen, statt sie zu erraten',
    welcomeText: 'Kurze Einheiten erklären Buchstabennamen, ihre Bedeutung und Zahlenwerte.',
    continue: 'Weiterlernen', startPractice: 'Übung starten',
    today: 'Heute', dailyGoal: 'Tagesziel', questions: 'Fragen',
    slavonicClock: 'Kirchenslawische Uhr', clockIntro: 'Die aktuelle Zeit in kirchenslawischen Buchstabenzahlen.',
    clockReference: 'gewohnte Schreibweise', clockZeroNote: '— steht für null; dafür gibt es in der Ziffernschrift kein eigenes Zeichen.',
    alphabet: 'Kirchenslawisches Alphabet', alphabetIntro: 'Tippe auf einen Buchstaben, um seine Karte zu öffnen.',
    learned: 'gelernt', back: 'Zurück', letterName: 'Name', meaning: 'Bedeutung des Namens',
    transliteration: 'Lesung', number: 'Zahlenwert', noNumber: 'nicht verwendet',
    markLearned: 'Als gelernt markieren', learnedDone: 'Buchstabe gelernt', nextLetter: 'Nächster Buchstabe',
    practiceTitle: 'Übung', practiceIntro: '10 Aufgaben ohne Zeitdruck. Fehler dürfen in Ruhe geprüft werden.',
    namesMode: 'Buchstaben und Namen', numbersMode: 'Zahlzeichen eingeschlossen', start: 'Starten',
    chooseName: 'Wie heißt dieser Buchstabe?', chooseGlyph: 'Wähle den Buchstaben', chooseNumber: 'Welchen Zahlenwert hat er?',
    correct: 'Richtig!', great: 'Starke Serie!', excellent: 'Großartig!', corrected: 'Jetzt stimmt es',
    wrong: 'Noch nicht', tapCorrect: 'Die richtige Antwort ist markiert', chooseHighlighted: 'Tippe auf die richtige Antwort',
    correctedHint: 'Antwort gefestigt', autoNext: 'nächste Aufgabe automatisch',
    streakLabel: 'Serie', pointsLabel: 'Punkte', bestStreak: 'beste Serie',
    next: 'Weiter', finish: 'Beenden',
    resultTitle: 'Einheit abgeschlossen', resultText: 'Richtige Antworten', retry: 'Noch einmal',
    settings: 'Einstellungen', interfaceLanguage: 'Sprache der Oberfläche', russian: 'Русский', german: 'Deutsch',
    goal: 'Tagesziel', progress: 'Gesamtfortschritt', accuracy: 'Genauigkeit', streak: 'Tage in Folge',
    reset: 'Lernfortschritt zurücksetzen', resetConfirm: 'Zum Bestätigen erneut tippen',
    onboardingTitle: 'Wähle zuerst deine Sprache', onboardingText: 'Du kannst sie später jederzeit im Profil ändern.',
    begin: 'Lernen beginnen', install: 'Installierbar', installText: 'Das ist optional — die App funktioniert vollständig im Browser.',
    offline: 'Nach dem ersten Öffnen offline verfügbar', loading: 'Dein Fortschritt wird geladen…',
    audioPreview: 'Namen anhören', audioExample: 'Beispiel anhören',
    exampleWithLetter: 'Beispiel mit dem Buchstaben', readInContext: 'Der Buchstabe im Text',
    exampleCorpus: 'Textquelle: Kirchenslawische Bibel, 1900 · Bible-Desktop-Korpus',
    systemVoiceNotice: 'Vorläufig wird die Systemstimme verwendet; eine eigene Sprecheraufnahme folgt.',
    speechUnavailable: 'Sprachausgabe ist in diesem Browser nicht verfügbar',
    mastered: 'Gelernte Buchstaben', totalAnswers: 'Antworten insgesamt'
  }
} as const

type MessageKey = keyof typeof messages.ru
const activeLocale = ref<Locale>('ru')

export const useI18n = () => {
  const t = (key: MessageKey): string => messages[activeLocale.value][key]
  const locale = computed(() => activeLocale.value)
  const setLocale = (next: Locale) => {
    activeLocale.value = next
    document.documentElement.lang = next
  }
  return { locale, setLocale, t }
}
