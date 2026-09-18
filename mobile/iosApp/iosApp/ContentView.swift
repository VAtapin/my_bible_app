import Foundation
import SharedLogic
import SwiftUI

enum SetupRoute: String {
    case welcome
    case sections
    case translations
    case summary
    case home
    case bible
    case prayers
    case calendar
}

enum TranslationFilter: String, CaseIterable, Identifiable {
    case all
    case russian = "ru"
    case german = "de"

    var id: String { rawValue }
    var code: String? { self == .all ? nil : rawValue }
}

struct SectionChoice: Identifiable {
    let id: String
    let titleKey: String
    let subtitleKey: String
    let symbol: String

    static let all = [
        SectionChoice(id: "bible", titleKey: "section.bible", subtitleKey: "section.bible.subtitle", symbol: "book.closed"),
        SectionChoice(id: "prayer", titleKey: "section.prayer", subtitleKey: "section.prayer.subtitle", symbol: "cross"),
        SectionChoice(id: "calendar", titleKey: "section.calendar", subtitleKey: "section.calendar.subtitle", symbol: "calendar"),
        SectionChoice(id: "study", titleKey: "section.study", subtitleKey: "section.study.subtitle", symbol: "bookmark"),
        SectionChoice(id: "reminders", titleKey: "section.reminders", subtitleKey: "section.reminders.subtitle", symbol: "bell"),
    ]
}

@MainActor
final class TranslationListModel: ObservableObject {
    @Published var translations: [TranslationSummary] = []
    @Published var isLoading = false
    @Published var hasError = false

    private let loader = TranslationsLoader()
    private var requestID = UUID()

    func load() {
        let currentRequest = UUID()
        requestID = currentRequest
        isLoading = true
        hasError = false

        loader.load(
            language: nil,
            onSuccess: { [weak self] values in
                guard let self, self.requestID == currentRequest else { return }
                self.translations = values
                self.isLoading = false
            },
            onError: { [weak self] _ in
                guard let self, self.requestID == currentRequest else { return }
                self.hasError = true
                self.isLoading = false
            }
        )
    }

    deinit {
        loader.close()
    }
}

struct ContentView: View {
    @AppStorage("setupComplete") private var setupComplete = false
    @AppStorage("uiLanguage") private var language = "ru"
    @AppStorage("sections") private var sectionsCSV = "bible,prayer,calendar"
    @AppStorage("translations") private var translationsCSV = ""

    @StateObject private var model = TranslationListModel()
    @State private var route = SetupRoute.welcome
    @State private var filter = TranslationFilter.all
    @State private var didRestoreRoute = false

    private var selectedSections: Set<String> {
        Set(sectionsCSV.split(separator: ",").map(String.init))
    }

    private var selectedTranslationCodes: Set<String> {
        Set(translationsCSV.split(separator: ",").map(String.init))
    }

    private var selectedTranslations: [TranslationSummary] {
        model.translations
            .filter { selectedTranslationCodes.contains($0.code) }
            .sorted { lhs, rhs in
                let lhsPreferred = lhs.language.code == language
                let rhsPreferred = rhs.language.code == language
                return lhsPreferred == rhsPreferred ? lhs.name < rhs.name : lhsPreferred
            }
    }

    var body: some View {
        Group {
            switch route {
            case .welcome:
                WelcomeView(
                    language: language,
                    onLanguageChange: { language = $0 },
                    onQuick: {
                        sectionsCSV = "bible,prayer,calendar"
                        route = .translations
                    },
                    onManual: { route = .sections }
                )
            case .sections:
                SectionsSetupView(
                    language: language,
                    selected: selectedSections,
                    onLanguageChange: { language = $0 },
                    onToggle: toggleSection,
                    onBack: { route = .welcome },
                    onNext: {
                        route = selectedSections.contains("bible") ? .translations : .summary
                    }
                )
            case .translations:
                TranslationsSetupView(
                    language: language,
                    translations: model.translations,
                    isLoading: model.isLoading,
                    hasError: model.hasError,
                    selectedCodes: selectedTranslationCodes,
                    filter: filter,
                    onFilterChange: { filter = $0 },
                    onToggle: toggleTranslation,
                    onRetry: model.load,
                    onBack: { route = .sections },
                    onNext: { route = .summary }
                )
            case .summary:
                SetupSummaryView(
                    language: language,
                    selectedSections: selectedSections,
                    selectedTranslations: selectedTranslations,
                    onBack: {
                        route = selectedSections.contains("bible") ? .translations : .sections
                    },
                    onCreate: {
                        setupComplete = true
                        route = .home
                    }
                )
            case .home:
                TodayView(
                    language: language,
                    selectedSections: selectedSections,
                    selectedTranslations: selectedTranslations,
                    onEdit: { route = .sections },
                    onOpenBible: { route = .bible },
                    onOpenPrayers: { route = .prayers },
                    onOpenCalendar: { route = .calendar }
                )
            case .bible:
                BibleBrowserView(
                    language: language,
                    translations: selectedTranslations,
                    onBack: { route = .home }
                )
            case .prayers:
                PrayersView(language: language, onBack: { route = .home })
            case .calendar:
                CalendarView(language: language, onBack: { route = .home })
            }
        }
        .tint(AppPalette.navy)
        .task {
            if model.translations.isEmpty {
                model.load()
            }
            guard !didRestoreRoute else { return }
            didRestoreRoute = true
            route = setupComplete ? .home : .welcome
        }
        .onChange(of: model.translations.count) { _ in
            ensureRecommendedTranslation()
        }
        .onChange(of: route) { newRoute in
            if newRoute == .translations {
                ensureRecommendedTranslation()
            }
        }
    }

    private func toggleSection(_ id: String) {
        var values = selectedSections
        if !values.insert(id).inserted {
            values.remove(id)
        }
        sectionsCSV = values.sorted().joined(separator: ",")
    }

    private func toggleTranslation(_ code: String) {
        var values = selectedTranslationCodes
        if !values.insert(code).inserted {
            values.remove(code)
        }
        translationsCSV = values.sorted().joined(separator: ",")
    }

    private func ensureRecommendedTranslation() {
        guard selectedTranslationCodes.isEmpty, !model.translations.isEmpty else { return }
        let recommended = model.translations.first {
            $0.language.code == language && $0.isDefault
        } ?? model.translations.first {
            $0.language.code == language
        } ?? model.translations.first

        if let recommended {
            translationsCSV = recommended.code
        }
    }
}
