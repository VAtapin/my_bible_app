import SharedLogic
import SwiftUI

struct WelcomeView: View {
    let language: String
    let onLanguageChange: (String) -> Void
    let onQuick: () -> Void
    let onManual: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                LanguagePicker(language: language, onChange: onLanguageChange)
                    .padding(.bottom, 28)

                Image("BrandIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 156, height: 156)
                    .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))

                Text("Bible Desktop")
                    .font(.system(size: 34, weight: .bold, design: .serif))
                    .foregroundStyle(AppPalette.ink)
                    .padding(.top, 22)

                Text(localized("welcome.title", language: language))
                    .font(.system(size: 24, weight: .semibold, design: .serif))
                    .foregroundStyle(AppPalette.navy)
                    .multilineTextAlignment(.center)
                    .padding(.top, 10)

                Text(localized("welcome.subtitle", language: language))
                    .font(.subheadline)
                    .foregroundStyle(AppPalette.blue)
                    .multilineTextAlignment(.center)
                    .padding(.top, 6)
                    .padding(.bottom, 28)

                WelcomeActionCard(
                    title: localized("setup.quick", language: language),
                    subtitle: localized("setup.quick.subtitle", language: language),
                    symbol: "bolt.fill",
                    primary: true,
                    action: onQuick
                )

                WelcomeActionCard(
                    title: localized("setup.manual", language: language),
                    subtitle: localized("setup.manual.subtitle", language: language),
                    symbol: "gearshape",
                    action: onManual
                )

                WelcomeActionCard(
                    title: localized("setup.restore", language: language),
                    subtitle: localized("setup.restore.subtitle", language: language),
                    symbol: "arrow.clockwise.icloud",
                    enabled: false,
                    action: {}
                )

                Text(localized("welcome.quote", language: language))
                    .font(.system(.body, design: .serif))
                    .foregroundStyle(AppPalette.blue)
                    .multilineTextAlignment(.center)
                    .padding(.top, 26)
            }
            .padding(24)
        }
        .background(AppPalette.cream.ignoresSafeArea())
    }
}

struct SectionsSetupView: View {
    let language: String
    let selected: Set<String>
    let onLanguageChange: (String) -> Void
    let onToggle: (String) -> Void
    let onBack: () -> Void
    let onNext: () -> Void

    var body: some View {
        SetupPage(
            language: language,
            step: 1,
            title: localized("sections.title", language: language),
            subtitle: localized("sections.subtitle", language: language),
            onBack: onBack,
            footer: {
                PrimaryButton(title: localized("action.next", language: language), action: onNext)
            }
        ) {
            Text(localized("language.title", language: language))
                .font(.headline)
                .foregroundStyle(AppPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)

            LanguagePicker(language: language, onChange: onLanguageChange)
                .padding(.top, 8)
                .padding(.bottom, 18)

            ForEach(SectionChoice.all) { section in
                SectionToggleRow(
                    section: section,
                    language: language,
                    isSelected: selected.contains(section.id),
                    onToggle: { onToggle(section.id) }
                )
            }
        }
    }
}

struct TranslationsSetupView: View {
    let language: String
    let translations: [TranslationSummary]
    let isLoading: Bool
    let hasError: Bool
    let selectedCodes: Set<String>
    let filter: TranslationFilter
    let onFilterChange: (TranslationFilter) -> Void
    let onToggle: (String) -> Void
    let onRetry: () -> Void
    let onBack: () -> Void
    let onNext: () -> Void

    private var visibleTranslations: [TranslationSummary] {
        translations.filter { filter.code == nil || $0.language.code == filter.code }
    }

    var body: some View {
        SetupPage(
            language: language,
            step: 2,
            title: localized("translations.setup.title", language: language),
            subtitle: localized("translations.setup.subtitle", language: language),
            onBack: onBack,
            scrolls: false,
            footer: {
                PrimaryButton(
                    title: localized("action.next", language: language),
                    enabled: !selectedCodes.isEmpty,
                    action: onNext
                )
            }
        ) {
            TranslationFilterPicker(
                language: language,
                selection: filter,
                onChange: onFilterChange
            )

            Text(localized("translations.selected", language: language, selectedCodes.count))
                .font(.caption)
                .foregroundStyle(AppPalette.blue)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 8)

            if isLoading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text(localized("translations.loading", language: language))
                        .foregroundStyle(AppPalette.blue)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if hasError {
                VStack(spacing: 12) {
                    Text(localized("translations.error", language: language))
                        .foregroundStyle(AppPalette.ink)
                    Button(localized("action.retry", language: language), action: onRetry)
                        .buttonStyle(.borderedProminent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(visibleTranslations, id: \.code) { translation in
                            TranslationSelectionRow(
                                translation: translation,
                                selected: selectedCodes.contains(translation.code),
                                onToggle: { onToggle(translation.code) }
                            )
                        }
                    }
                    .padding(.bottom, 8)
                }
            }
        }
    }
}

struct SetupSummaryView: View {
    let language: String
    let selectedSections: Set<String>
    let selectedTranslations: [TranslationSummary]
    let onBack: () -> Void
    let onCreate: () -> Void

    var body: some View {
        SetupPage(
            language: language,
            step: 3,
            title: localized("summary.title", language: language),
            subtitle: localized("summary.subtitle", language: language),
            onBack: onBack,
            footer: {
                PrimaryButton(
                    title: localized("summary.create", language: language),
                    symbol: "checkmark.circle.fill",
                    action: onCreate
                )
            }
        ) {
            SummaryGroup(
                title: localized("summary.language", language: language),
                values: [language == "de" ? "Deutsch" : "Русский"]
            )
            SummaryGroup(
                title: localized("summary.sections", language: language),
                values: SectionChoice.all
                    .filter { selectedSections.contains($0.id) }
                    .map { localized($0.titleKey, language: language) }
            )
            if selectedSections.contains("bible") {
                SummaryGroup(
                    title: localized("summary.translations", language: language),
                    values: selectedTranslations.map(\.name)
                )
            }

            Label(
                localized("summary.offline", language: language),
                systemImage: "arrow.down.circle"
            )
            .font(.caption)
            .foregroundStyle(AppPalette.blue)
            .padding(.top, 4)
        }
    }
}

struct TodayView: View {
    let language: String
    let selectedSections: Set<String>
    let selectedTranslations: [TranslationSummary]
    let onEdit: () -> Void
    let onOpenBible: () -> Void
    let onOpenPrayers: () -> Void
    let onOpenCalendar: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image("BrandIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text("Bible Desktop")
                        .font(.system(.headline, design: .serif).weight(.bold))
                        .foregroundStyle(AppPalette.ink)
                    Text(localized("today.myday", language: language))
                        .font(.caption)
                        .foregroundStyle(AppPalette.blue)
                }

                Spacer()

                Button(action: onEdit) {
                    Image(systemName: "gearshape")
                        .font(.title3)
                }
                .accessibilityLabel(localized("setup.manual", language: language))
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(Color.white)

            ScrollView {
                LazyVStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(localized("today.title", language: language))
                            .font(.system(size: 30, weight: .bold, design: .serif))
                        Text(localized("today.quote", language: language))
                            .font(.system(.title3, design: .serif))
                            .foregroundStyle(AppPalette.lightBlue)
                    }
                    .foregroundStyle(Color.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(22)
                    .background(AppPalette.navy, in: RoundedRectangle(cornerRadius: 22, style: .continuous))

                    ForEach(SectionChoice.all.filter { selectedSections.contains($0.id) }) { section in
                        if section.id == "bible" || section.id == "prayer" || section.id == "calendar" {
                            Button(action: {
                                switch section.id {
                                case "bible": onOpenBible()
                                case "prayer": onOpenPrayers()
                                case "calendar": onOpenCalendar()
                                default: break
                                }
                            }) {
                                HomeSectionCard(
                                    section: section,
                                    language: language,
                                    trailing: section.id == "bible" ? selectedTranslations.first?.shortName : nil,
                                    showsDisclosure: true
                                )
                            }
                            .buttonStyle(.plain)
                        } else {
                            HomeSectionCard(
                                section: section,
                                language: language,
                                trailing: nil,
                                showsDisclosure: false
                            )
                        }
                    }
                }
                .padding(18)
            }

            HomeNavigation(
                language: language,
                onOpenBible: onOpenBible,
                onOpenPrayers: onOpenPrayers,
                onOpenCalendar: onOpenCalendar
            )
        }
        .background(AppPalette.cream.ignoresSafeArea())
    }
}

private struct SetupPage<Content: View, Footer: View>: View {
    let language: String
    let step: Int
    let title: String
    let subtitle: String
    let onBack: () -> Void
    let scrolls: Bool
    let footer: () -> Footer
    let content: () -> Content

    init(
        language: String,
        step: Int,
        title: String,
        subtitle: String,
        onBack: @escaping () -> Void,
        scrolls: Bool = true,
        @ViewBuilder footer: @escaping () -> Footer,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.language = language
        self.step = step
        self.title = title
        self.subtitle = subtitle
        self.onBack = onBack
        self.scrolls = scrolls
        self.footer = footer
        self.content = content
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .frame(width: 44, height: 44)
                }
                Spacer()
                Text(localized("setup.step", language: language, step))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(AppPalette.blue)
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .padding(.horizontal, 8)

            ProgressView(value: Double(step), total: 3)
                .tint(AppPalette.navy)

            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(size: 28, weight: .bold, design: .serif))
                    .foregroundStyle(AppPalette.ink)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(AppPalette.blue)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 18)

            Group {
                if scrolls {
                    ScrollView {
                        VStack(spacing: 9, content: content)
                    }
                } else {
                    VStack(spacing: 9, content: content)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 20)

            footer()
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(AppPalette.cream.shadow(color: .black.opacity(0.08), radius: 5, y: -2))
        }
        .background(AppPalette.cream.ignoresSafeArea())
    }
}

private struct WelcomeActionCard: View {
    let title: String
    let subtitle: String
    let symbol: String
    var primary = false
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.title3)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).opacity(0.78)
                }
                Spacer()
                Image(systemName: "chevron.right")
            }
            .foregroundStyle(primary ? Color.white : AppPalette.ink)
            .padding(18)
            .frame(maxWidth: .infinity)
            .background(
                primary ? AppPalette.navy : Color.white,
                in: RoundedRectangle(cornerRadius: 18, style: .continuous)
            )
            .overlay {
                if !primary {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(AppPalette.border, lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.55)
        .padding(.bottom, 12)
    }
}

private struct PrimaryButton: View {
    let title: String
    var symbol: String?
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                if let symbol {
                    Image(systemName: symbol)
                }
                Text(title).fontWeight(.semibold)
                if symbol == nil {
                    Image(systemName: "arrow.right")
                }
            }
            .frame(maxWidth: .infinity, minHeight: 52)
        }
        .buttonStyle(.borderedProminent)
        .disabled(!enabled)
    }
}

private struct LanguagePicker: View {
    let language: String
    let onChange: (String) -> Void

    var body: some View {
        HStack(spacing: 10) {
            languageButton(code: "ru", title: "Русский")
            languageButton(code: "de", title: "Deutsch")
        }
    }

    private func languageButton(code: String, title: String) -> some View {
        Button {
            onChange(code)
        } label: {
            HStack {
                if language == code {
                    Image(systemName: "globe")
                }
                Text(title)
                    .font(.subheadline.weight(.medium))
            }
            .foregroundStyle(language == code ? Color.white : AppPalette.ink)
            .frame(maxWidth: .infinity, minHeight: 42)
            .background(
                language == code ? AppPalette.navy : Color.white,
                in: Capsule()
            )
            .overlay {
                Capsule().stroke(AppPalette.border, lineWidth: language == code ? 0 : 1)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct SectionToggleRow: View {
    let section: SectionChoice
    let language: String
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                Image(systemName: section.symbol)
                    .font(.title3)
                    .foregroundStyle(AppPalette.navy)
                    .frame(width: 44, height: 44)
                    .background(AppPalette.lightBlue, in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 3) {
                    Text(localized(section.titleKey, language: language))
                        .font(.headline)
                        .foregroundStyle(AppPalette.ink)
                    Text(localized(section.subtitleKey, language: language))
                        .font(.caption)
                        .foregroundStyle(AppPalette.blue)
                }
                Spacer()
                Toggle("", isOn: .constant(isSelected))
                    .labelsHidden()
                    .allowsHitTesting(false)
            }
            .padding(14)
            .background(Color.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(AppPalette.border, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct TranslationFilterPicker: View {
    let language: String
    let selection: TranslationFilter
    let onChange: (TranslationFilter) -> Void

    var body: some View {
        HStack(spacing: 8) {
            filterButton(.all, localized("language.all", language: language))
            filterButton(.russian, "Русский")
            filterButton(.german, "Deutsch")
        }
    }

    private func filterButton(_ filter: TranslationFilter, _ title: String) -> some View {
        Button(action: { onChange(filter) }) {
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundStyle(selection == filter ? Color.white : AppPalette.ink)
                .frame(maxWidth: .infinity, minHeight: 38)
                .background(selection == filter ? AppPalette.navy : Color.white, in: Capsule())
                .overlay { Capsule().stroke(AppPalette.border, lineWidth: selection == filter ? 0 : 1) }
        }
        .buttonStyle(.plain)
    }
}

private struct TranslationSelectionRow: View {
    let translation: TranslationSummary
    let selected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                Text(translation.language.code.uppercased())
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.white)
                    .frame(width: 40, height: 40)
                    .background(selected ? AppPalette.navy : AppPalette.blue, in: RoundedRectangle(cornerRadius: 10))

                VStack(alignment: .leading, spacing: 3) {
                    Text(translation.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppPalette.ink)
                        .multilineTextAlignment(.leading)
                    Text([translation.shortName, translation.language.name]
                        .compactMap { $0 }
                        .joined(separator: " · "))
                        .font(.caption)
                        .foregroundStyle(AppPalette.blue)
                }

                Spacer()
                Image(systemName: selected ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundStyle(selected ? AppPalette.navy : AppPalette.blue)
            }
            .padding(12)
            .background(selected ? AppPalette.lightBlue : Color.white, in: RoundedRectangle(cornerRadius: 15))
            .overlay {
                RoundedRectangle(cornerRadius: 15)
                    .stroke(AppPalette.border, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct SummaryGroup: View {
    let title: String
    let values: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .foregroundStyle(AppPalette.navy)
            ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                if index > 0 { Divider() }
                Label(value, systemImage: "checkmark.circle")
                    .font(.subheadline)
                    .foregroundStyle(AppPalette.ink)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        .overlay { RoundedRectangle(cornerRadius: 16).stroke(AppPalette.border, lineWidth: 1) }
    }
}

private struct HomeSectionCard: View {
    let section: SectionChoice
    let language: String
    let trailing: String?
    let showsDisclosure: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: section.symbol)
                .font(.title3)
                .foregroundStyle(AppPalette.navy)
                .frame(width: 46, height: 46)
                .background(AppPalette.lightBlue, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text(localized(section.titleKey, language: language))
                    .font(.headline)
                    .foregroundStyle(AppPalette.ink)
                Text(trailing ?? localized(section.subtitleKey, language: language))
                    .font(.caption)
                    .foregroundStyle(AppPalette.blue)
            }
            Spacer()
            if showsDisclosure {
                Image(systemName: "chevron.right")
                    .foregroundStyle(AppPalette.blue)
            }
        }
        .padding(16)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18))
        .overlay { RoundedRectangle(cornerRadius: 18).stroke(AppPalette.border, lineWidth: 1) }
    }
}

private struct HomeNavigation: View {
    let language: String
    let onOpenBible: () -> Void
    let onOpenPrayers: () -> Void
    let onOpenCalendar: () -> Void

    private let items = [
        ("house.fill", "nav.today"),
        ("book.closed", "nav.bible"),
        ("cross", "nav.prayers"),
        ("calendar", "nav.calendar"),
        ("line.3.horizontal", "nav.more"),
    ]

    var body: some View {
        HStack {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                Button(action: {
                    switch index {
                    case 1: onOpenBible()
                    case 2: onOpenPrayers()
                    case 3: onOpenCalendar()
                    default: break
                    }
                }) {
                    VStack(spacing: 3) {
                        Image(systemName: item.0)
                            .font(.body)
                        Text(localized(item.1, language: language))
                            .font(.system(size: 9))
                    }
                    .foregroundStyle(index == 0 ? AppPalette.navy : AppPalette.blue)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .disabled(index > 3)
            }
        }
        .padding(.top, 10)
        .padding(.bottom, 4)
        .background(Color.white)
    }
}
