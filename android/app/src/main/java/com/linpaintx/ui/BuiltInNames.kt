package com.linpaintx.ui

/** Russian display names for built-in sets and brushes, including libraries created before localization. */
private val BuiltInNames = mapOf(
    "Sketching" to "Наброски", "Inking" to "Обводка", "Painting" to "Живопись",
    "Airbrushing" to "Аэрография", "Charcoals" to "Уголь", "Textures" to "Текстуры",
    "Drawing" to "Рисование", "Watercolour" to "Акварель", "Calligraphy" to "Каллиграфия",
    "Spray Paint" to "Аэрозольная краска", "Nature" to "Природа", "Effects" to "Эффекты",
    "Pixel" to "Пиксельные", "Elements" to "Стихии", "Industrial" to "Индустриальные",
    "Organic" to "Органические", "Abstract" to "Абстракция", "Touchups" to "Ретушь",
    "Retro" to "Ретро", "Vintage" to "Винтаж", "Luminance" to "Свечение",

    "Graphite" to "Графит", "Soft Pencil" to "Мягкий карандаш", "Technical Pencil" to "Технический карандаш",
    "Lead Holder" to "Цанговый карандаш", "Coloured Pencil" to "Цветной карандаш", "Sketch Pen" to "Перо для набросков",
    "HB Pencil" to "Карандаш HB", "6B Pencil" to "Карандаш 6B", "Graphite Stick" to "Графитовый стержень",
    "Charcoal Pencil" to "Угольный карандаш", "Shading Pencil" to "Карандаш для штриховки", "Ballpoint" to "Шариковая ручка",
    "Round Pen" to "Круглое перо", "Fine Liner" to "Линер", "Brush Pen" to "Кисть-перо",
    "Dip Pen" to "Перьевая ручка", "Marker" to "Маркер", "Dry Ink" to "Сухие чернила",
    "Studio Ink" to "Студийные чернила", "Gel Pen" to "Гелевая ручка", "Thick Liner" to "Толстый линер",
    "Felt Tip" to "Фломастер", "Rough Ink" to "Грубые чернила", "Ink Bleed" to "Растекающиеся чернила",

    "Round Brush" to "Круглая кисть", "Flat Brush" to "Плоская кисть", "Bristle Brush" to "Щетинная кисть",
    "Dry Brush" to "Сухая кисть", "Gouache" to "Гуашь", "Oil Paint" to "Масляная краска",
    "Palette Knife" to "Мастихин", "Filbert" to "Овальная кисть", "Fan Brush" to "Веерная кисть",
    "Rigger" to "Лайнер", "Sable" to "Соболь", "Impasto" to "Импасто", "Acrylic" to "Акрил",
    "Tempera" to "Темпера", "Scumble" to "Лессировка",
    "Soft Airbrush" to "Мягкий аэрограф", "Hard Airbrush" to "Жёсткий аэрограф", "Medium Airbrush" to "Средний аэрограф",
    "Fine Mist" to "Мелкий туман", "Flat Airbrush" to "Плоский аэрограф", "Grainy Airbrush" to "Зернистый аэрограф",
    "Stipple Airbrush" to "Точечный аэрограф", "Feather Airbrush" to "Перьевой аэрограф", "Tight Airbrush" to "Точный аэрограф",

    "Charcoal Stick" to "Угольная палочка", "Willow Charcoal" to "Ивовый уголь", "Compressed Charcoal" to "Прессованный уголь",
    "Vine Charcoal" to "Виноградный уголь", "Charcoal Dust" to "Угольная пыль", "Charcoal Block" to "Угольный брусок",
    "Burnt Stick" to "Обожжённая палочка", "Smudged Charcoal" to "Растушёванный уголь", "Soft Charcoal" to "Мягкий уголь",
    "Speckles" to "Крапинки", "Canvas Texture" to "Текстура холста", "Dot Screen" to "Точечный растр",
    "Crosshatch" to "Перекрёстная штриховка", "Rough Wall" to "Грубая стена", "Grit" to "Зерно",
    "Stipple" to "Точки", "Wood Grain" to "Текстура дерева", "Brickwork" to "Кирпичная кладка",
    "Fish Scales" to "Рыбья чешуя", "Cell Wall" to "Клеточная структура", "Marble" to "Мрамор",
    "Pinstripe" to "Тонкие полосы", "Honeycomb" to "Соты", "Leather" to "Кожа",
    "Sandpaper" to "Наждачная бумага", "Checkerboard" to "Шахматная клетка",

    "Chalk" to "Мел", "Soft Pastel" to "Мягкая пастель", "Oil Pastel" to "Масляная пастель",
    "Wax Crayon" to "Восковой мелок", "Conte" to "Конте", "Hard Pastel" to "Твёрдая пастель",
    "Chalk Pencil" to "Меловой карандаш", "Pastel Pencil" to "Пастельный карандаш", "Litho Crayon" to "Литографский мелок",
    "Wet Wash" to "Мокрая заливка", "Round Wash" to "Круглая заливка", "Granulating Wash" to "Гранулирующая заливка",
    "Blot" to "Клякса", "Bleeding Wash" to "Растекающаяся заливка", "Flat Wash" to "Плоская заливка",
    "Dry Wash" to "Сухая заливка", "Salt" to "Соль", "Splash" to "Брызги", "Mop Brush" to "Кисть-моп",

    "Chisel Nib" to "Плоское перо", "Script Brush" to "Рукописная кисть", "Monoline" to "Монолиния",
    "Ribbon" to "Лента", "Pointed Nib" to "Острое перо", "Broad Pen" to "Широкое перо",
    "Italic Nib" to "Курсивное перо", "Felt Brush" to "Фетровая кисть", "Copperplate" to "Медное перо",
    "Spray Can" to "Баллончик", "Fat Cap" to "Широкая насадка", "Flick" to "Взмах", "Mist" to "Туман",
    "Drips" to "Потёки", "Hard Spray" to "Жёсткий спрей", "Splatter Can" to "Баллончик с брызгами", "Skinny Cap" to "Тонкая насадка",

    "Leaves" to "Листья", "Grass" to "Трава", "Clouds" to "Облака", "Bark" to "Кора",
    "Foliage" to "Листва", "Pine" to "Хвоя", "Twigs" to "Веточки", "Moss" to "Мох",
    "Flowers" to "Цветы", "Fur" to "Мех", "Pebbles" to "Галька", "Feathers" to "Перья", "Reeds" to "Камыш",
    "Sparkle" to "Искры", "Bokeh" to "Боке", "Smoke" to "Дым", "Snow" to "Снег",
    "Light Trail" to "Световой след", "Glow" to "Свечение", "Stars" to "Звёзды", "Bubbles" to "Пузыри",
    "Embers" to "Угли", "Rain" to "Дождь", "Snowflakes" to "Снежинки",

    "Dither" to "Дизеринг", "Hatching Pen" to "Перо для штриховки", "Pixel Checker" to "Пиксельная клетка",
    "Pixel Spray" to "Пиксельный спрей", "Pixel Round" to "Круглый пиксель", "Pixel Line" to "Пиксельная линия",
    "Fire" to "Огонь", "Smoke Puff" to "Клуб дыма", "Water Drops" to "Капли воды", "Ripples" to "Рябь",
    "Lightning" to "Молния", "Ice" to "Лёд", "Sea Foam" to "Морская пена", "Fog" to "Туман",
    "Rusted" to "Ржавчина", "Cement" to "Цемент", "Grunge" to "Гранж", "Scratched Metal" to "Царапанный металл",
    "Steel Wool" to "Стальная вата", "Rivets" to "Заклёпки", "Grime" to "Грязь", "Oil Stain" to "Масляное пятно",
    "Hair" to "Волосы", "Skin Pores" to "Поры кожи", "Freckles" to "Веснушки", "Reptile" to "Чешуя рептилии",
    "Coral" to "Коралл", "Lichen" to "Лишайник", "Seeds" to "Семена", "Veins" to "Прожилки",

    "Hearts" to "Сердца", "Confetti" to "Конфетти", "Triangles" to "Треугольники", "Diamonds" to "Ромбы",
    "Speed Lines" to "Линии скорости", "Scribbles" to "Каракули", "Dot Trail" to "Точечный след", "Dashes" to "Штрихи",
    "Soft Blender" to "Мягкая растушёвка", "Grainy Blender" to "Зернистая растушёвка", "Retouch" to "Ретушь",
    "Highlight" to "Свет", "Shadow" to "Тень", "Noise" to "Шум", "Fine Detail" to "Мелкие детали",
    "Ben Day" to "Растр Бен-Дея", "Screen Tone" to "Экранный тон", "Comic Ink" to "Комиксные чернила",
    "Retro Stipple" to "Ретро-точки", "Pop Dots" to "Поп-точки", "Retro Lines" to "Ретро-линии", "Offset Print" to "Офсетная печать",
    "Worn Ink" to "Потёртые чернила", "Letterpress" to "Высокая печать", "Old Paper" to "Старая бумага",
    "Sepia Wash" to "Сепия", "Scratchy Pen" to "Царапающее перо", "Rubber Stamp" to "Резиновый штамп", "Faded Print" to "Выцветшая печать",
    "Neon" to "Неон", "Glimmer" to "Мерцание", "Lens Dust" to "Пыль в объективе", "Flare" to "Блик",
    "Halo" to "Ореол", "Light Leak" to "Засветка", "Aurora" to "Сияние", "Fairy Dust" to "Волшебная пыль",
    "Soft Glow" to "Мягкое свечение", "Overlay Light" to "Наложенный свет",
)

fun localizedBuiltInName(name: String): String = BuiltInNames[name] ?: name

private val StudioNames = mapOf(
    "Stroke path" to "Траектория штриха", "Stroke properties" to "Свойства штриха", "Spacing" to "Интервал",
    "Spacing Jitter" to "Разброс интервала", "Jitter Lateral" to "Поперечный разброс", "Jitter Linear" to "Продольный разброс",
    "Fall off" to "Затухание", "StreamLine" to "Плавность линии", "Amount" to "Сила", "Pressure" to "Нажим",
    "Motion filtering" to "Фильтрация движения", "Expression" to "Характер", "Taper" to "Сужение",
    "Pressure taper" to "Сужение от нажима", "Touch taper" to "Сужение от касания", "Taper properties" to "Свойства сужения",
    "Size" to "Размер", "Tip" to "Кончик", "Tip animation" to "Анимация кончика", "Classic taper" to "Классическое сужение",
    "Shape" to "Форма", "Shape Source" to "Источник формы", "Input style" to "Способ ввода", "Touch only" to "Только касание",
    "Azimuth" to "Азимут", "Azimuth and barrel roll" to "Азимут и вращение пера", "Relative to stroke" to "Относительно штриха",
    "Touch properties" to "Свойства касания", "Shape properties" to "Свойства формы", "Scatter" to "Рассеивание",
    "Count" to "Количество", "Count jitter" to "Разброс количества", "Randomized" to "Случайно", "Flip X" to "Отразить по X",
    "Flip Y" to "Отразить по Y", "Pressure Roundness" to "Округлость от нажима", "Tilt roundness" to "Округлость от наклона",
    "Roundness Vertical Jitter" to "Вертикальный разброс округлости", "Roundness Horizontal Jitter" to "Горизонтальный разброс округлости",
    "Shape filtering" to "Фильтрация формы", "No filtering" to "Без фильтрации", "Classic filtering" to "Классическая фильтрация",
    "Improved filtering" to "Улучшенная фильтрация", "Grain" to "Зерно", "Grain Source" to "Источник зерна",
    "Grain behavior" to "Поведение зерна", "Moving" to "Движущееся", "Texturized" to "Текстурированное", "Movement" to "Движение",
    "Scale" to "Масштаб", "Zoom" to "Увеличение", "Depth" to "Глубина", "Depth minimum" to "Минимальная глубина",
    "Depth jitter" to "Разброс глубины", "Offset jitter" to "Разброс смещения", "Blend mode" to "Режим смешивания",
    "Brightness" to "Яркость", "Contrast" to "Контраст", "Grain filtering" to "Фильтрация зерна",
    "3D grain behaviour" to "Поведение 3D-зерна", "Grain follows camera" to "Зерно следует за камерой",
    "Rendering" to "Отрисовка", "Rendering mode" to "Режим отрисовки", "Light Glaze" to "Лёгкая глазурь",
    "Uniformed Glaze" to "Равномерная глазурь", "Intense Glaze" to "Насыщенная глазурь", "Heavy Glaze" to "Плотная глазурь",
    "Uniform Blending" to "Равномерное смешивание", "Intense Blending" to "Интенсивное смешивание", "Blending" to "Смешивание",
    "Flow" to "Поток", "Wet edges" to "Мокрые края", "Burnt edges" to "Тёмные края", "Burnt edges mode" to "Режим тёмных краёв",
    "Luminance blending" to "Смешивание яркости", "Alpha Threshold" to "Порог альфа-канала", "Threshold Amount" to "Величина порога",
    "Classic Normal Combine mode" to "Классическое обычное смешивание", "Wet Mix" to "Влажное смешивание", "Wet mix" to "Влажное смешивание",
    "Dilution" to "Разбавление", "Charge" to "Запас краски", "Attack" to "Нанесение", "Pull" to "Растягивание",
    "Grade" to "Градация", "Blur" to "Размытие", "Blur jitter" to "Разброс размытия", "Wetness jitter" to "Разброс влажности",
    "Color dynamics" to "Динамика цвета", "Stamp color jitter" to "Разброс цвета отпечатка", "Stroke color jitter" to "Разброс цвета штриха",
    "Color pressure" to "Цвет от нажима", "Color tilt" to "Цвет от наклона", "Color barrel roll" to "Цвет от вращения пера",
    "Hue" to "Оттенок", "Saturation" to "Насыщенность", "Lightness" to "Светлота", "Darkness" to "Затемнение",
    "Secondary color" to "Вторичный цвет", "Secondary Color" to "Вторичный цвет", "Dynamics" to "Динамика", "Speed" to "Скорость",
    "Jitter" to "Разброс", "Tilt" to "Наклон", "Gradation" to "Градация", "Bleed" to "Растекание",
    "Size compression" to "Сжатие размера", "Barrel roll" to "Вращение пера", "Cursor outline" to "Контур курсора",
    "None" to "Нет", "Active color" to "Активный цвет", "Hover" to "Наведение", "Estimated pressure" to "Расчётный нажим",
    "Hover fill" to "Заливка при наведении", "All" to "Всё", "Properties" to "Свойства", "Brush properties" to "Свойства кисти",
    "Orient to screen" to "Ориентировать по экрану", "Smudge Pull" to "Сила размазывания", "Brush behavior" to "Поведение кисти",
    "Maximum size" to "Максимальный размер", "Minimum size" to "Минимальный размер", "Maximum opacity" to "Максимальная непрозрачность",
    "Minimum opacity" to "Минимальная непрозрачность", "Materials" to "Материалы", "3D Materials" to "3D-материалы",
    "Metallic" to "Металл", "Metallic Source" to "Источник металличности", "Roughness" to "Шероховатость",
    "Roughness Source" to "Источник шероховатости", "Preview render" to "Предпросмотр отрисовки", "Preview properties" to "Свойства предпросмотра",
    "Use stamp preview" to "Показывать отпечаток", "Pressure Minimum" to "Минимальный нажим", "Pressure Scale" to "Масштаб нажима",
    "Tilt angle" to "Угол наклона", "About this brush" to "Об этой кисти",
    "Normal" to "Обычный", "Darken" to "Затемнение", "Multiply" to "Умножение", "Color Burn" to "Затемнение основы",
    "Linear Burn" to "Линейное затемнение", "Darker Color" to "Темнее", "Lighten" to "Осветление", "Screen" to "Экран",
    "Color Dodge" to "Осветление основы", "Add" to "Сложение", "Lighter Color" to "Светлее", "Overlay" to "Перекрытие",
    "Soft Light" to "Мягкий свет", "Hard Light" to "Жёсткий свет", "Vivid Light" to "Яркий свет",
    "Linear Light" to "Линейный свет", "Pin Light" to "Точечный свет", "Hard Mix" to "Жёсткое смешивание",
    "Difference" to "Разница", "Exclusion" to "Исключение", "Subtract" to "Вычитание", "Divide" to "Деление",
    "Color" to "Цвет", "Luminosity" to "Яркость",
)

fun localizedStudioName(name: String): String = StudioNames[name] ?: localizedBuiltInName(name)

/**
 * The name an artwork carries when nobody gave it one. The core and the view model store these
 * English names (they live inside the project folder and in files already saved), so what shows
 * them translates them here — the gallery's titles and alerts, and a share or an export's name.
 */
fun localizedArtworkName(name: String): String = when (name) {
    "Untitled Artwork" -> "Новая работа"
    "Untitled Canvas" -> "Новый холст"
    else -> name
}
