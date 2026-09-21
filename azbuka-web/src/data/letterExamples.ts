import type { LocalizedText } from '@/domain/types'

export interface LetterExample {
  text: string
  translation: LocalizedText
  source: LocalizedText
}

const examples = {
  john14: {
    text: 'Гл҃а є҆мꙋ̀ і҆и҃съ: а҆́зъ є҆́смь пꙋ́ть и҆ и҆́стина и҆ живо́тъ: никто́же прїи́детъ ко ѻ҆ц҃ꙋ̀, то́кмѡ мно́ю:',
    translation: {
      ru: 'Иисус сказал ему: Я есмь путь, истина и жизнь; никто не приходит к Отцу, как только через Меня.',
      de: 'Jesus spricht zu ihm: Ich bin der Weg, die Wahrheit und das Leben; niemand kommt zum Vater außer durch mich.',
    },
    source: { ru: 'Ин 14:6', de: 'Joh 14,6' },
  },
  genesis1: {
    text: 'Въ нача́лѣ сотворѝ бг҃ъ не́бо и҆ зе́млю.',
    translation: {
      ru: 'В начале сотворил Бог небо и землю.',
      de: 'Am Anfang schuf Gott Himmel und Erde.',
    },
    source: { ru: 'Быт 1:1', de: 'Gen 1,1' },
  },
  genesis4: {
    text: 'И҆ ви́дѣ бг҃ъ свѣ́тъ, ꙗ҆́кѡ добро̀, и҆ разлꙋчѝ бг҃ъ междꙋ̀ свѣ́томъ и҆ междꙋ̀ тьмо́ю.',
    translation: {
      ru: 'И увидел Бог свет, что он хорош, и отделил Бог свет от тьмы.',
      de: 'Gott sah, dass das Licht gut war, und schied das Licht von der Finsternis.',
    },
    source: { ru: 'Быт 1:4', de: 'Gen 1,4' },
  },
  genesis30Beasts: {
    text: 'и҆ всѣ̑мъ ѕвѣрє́мъ зємны́мъ, и҆ всѣ̑мъ пти́цамъ небє́снымъ.',
    translation: {
      ru: 'И всем земным зверям, и всем небесным птицам.',
      de: 'Und allen Tieren der Erde und allen Vögeln des Himmels.',
    },
    source: { ru: 'Быт 1:30, фрагмент', de: 'Gen 1,30, Auszug' },
  },
  genesis30Soul: {
    text: 'и҆́же и҆́мать въ себѣ̀ дꙋ́шꙋ живота̀.',
    translation: {
      ru: 'Которое имеет в себе душу живую.',
      de: 'Das eine lebendige Seele in sich hat.',
    },
    source: { ru: 'Быт 1:30, фрагмент', de: 'Gen 1,30, Auszug' },
  },
  genesis30End: {
    text: 'И҆ бы́сть та́кѡ.',
    translation: { ru: 'И стало так.', de: 'Und es geschah so.' },
    source: { ru: 'Быт 1:30, окончание', de: 'Gen 1,30, Schluss' },
  },
  psalm1: {
    text: 'и҆ всѧ̑, є҆ли̑ка а҆́ще твори́тъ, ᲂу҆спѣ́етъ.',
    translation: { ru: 'И во всём, что он делает, преуспеет.', de: 'Und alles, was er tut, wird gelingen.' },
    source: { ru: 'Пс 1:3, фрагмент', de: 'Ps 1,3, Auszug' },
  },
  thomas: {
    text: 'Ѳѡма́ же, є҆ди́нъ ѿ ѻ҆боюна́десѧте, глаго́лемый близне́цъ, не бѣ̀ съ ни́ми.',
    translation: { ru: 'Фома же, один из двенадцати, называемый Близнец, не был с ними.', de: 'Thomas aber, einer der Zwölf, genannt Zwilling, war nicht bei ihnen.' },
    source: { ru: 'Ин 20:24, фрагмент', de: 'Joh 20,24, Auszug' },
  },
  alexander: {
    text: 'А҆леѯа́ндръ же, помаа́въ рꙋко́ю, хотѧ́ше ѿвѣща́ти наро́дꙋ.',
    translation: { ru: 'Александр же, дав знак рукой, хотел говорить народу.', de: 'Alexander gab mit der Hand ein Zeichen und wollte zum Volk sprechen.' },
    source: { ru: 'Деян 19:33, фрагмент', de: 'Apg 19,33, Auszug' },
  },
  pharaoh: {
    text: 'Заповѣ́да же фараѡ́нъ всѣ̑мъ лю́демъ свои̑мъ.',
    translation: { ru: 'И повелел фараон всему своему народу.', de: 'Der Pharao gebot seinem ganzen Volk.' },
    source: { ru: 'Исх 1:22, фрагмент', de: 'Ex 1,22, Auszug' },
  },
  christ: {
    text: 'Кни́га родства̀ і҆и҃са хрⷭ҇та̀, сн҃а дв҃дова, сн҃а а҆враа́млѧ.',
    translation: { ru: 'Родословие Иисуса Христа, Сына Давидова, Сына Авраамова.', de: 'Das Geschlechtsregister Jesu Christi, des Sohnes Davids, des Sohnes Abrahams.' },
    source: { ru: 'Мф 1:1', de: 'Mt 1,1' },
  },
  psalms: {
    text: 'И҆ благолѣ̑пны ѱалмы̀ і҆и҃лєвы.',
    translation: { ru: 'И благолепны псалмы Израилевы.', de: 'Und schön sind die Psalmen Israels.' },
    source: { ru: '2 Цар 23:1, фрагмент', de: '2 Sam 23,1, Auszug' },
  },
  king: {
    text: 'Воста́ же ца́рь и҆́нъ во є҆гѵ́птѣ, и҆́же не зна́ше і҆ѡ́сифа.',
    translation: { ru: 'И восстал в Египте иной царь, который не знал Иосифа.', de: 'Da kam ein anderer König in Ägypten auf, der Josef nicht kannte.' },
    source: { ru: 'Исх 1:8', de: 'Ex 1,8' },
  },
} satisfies Record<string, LetterExample>

const exampleIdsByLetter: Record<string, keyof typeof examples> = {
  az: 'john14',
  buki: 'genesis1',
  vedi: 'genesis1',
  glagol: 'john14',
  dobro: 'genesis4',
  yest: 'john14',
  zhivete: 'john14',
  zelo: 'genesis30Beasts',
  zemlya: 'genesis1',
  izhe: 'john14',
  fita: 'thomas',
  'i-decimal': 'john14',
  kako: 'genesis4',
  lyudi: 'john14',
  myslete: 'john14',
  nash: 'john14',
  ksi: 'alexander',
  on: 'john14',
  pokoy: 'john14',
  cherv: 'genesis4',
  rtsy: 'john14',
  slovo: 'john14',
  tverdo: 'john14',
  uk: 'john14',
  fert: 'pharaoh',
  kher: 'christ',
  psi: 'psalms',
  omega: 'genesis4',
  tsy: 'king',
  sha: 'genesis30Soul',
  shta: 'psalm1',
  yer: 'genesis1',
  yery: 'genesis30End',
  'yer-soft': 'genesis30End',
  yat: 'genesis1',
  yu: 'john14',
  ya: 'genesis4',
}

export const letterExampleById = (letterId: string): LetterExample | undefined => {
  const exampleId = exampleIdsByLetter[letterId]
  return exampleId ? examples[exampleId] : undefined
}

export const letterExampleIds = Object.keys(exampleIdsByLetter)
