import Foundation
import SharedLogic
import SwiftUI

private enum LanguageFilter: String, CaseIterable, Identifiable {
    case all
    case russian = "ru"
    case german = "de"

    var id: String { rawValue }
    var apiCode: String? { self == .all ? nil : rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .all: "language.all"
        case .russian: "language.russian"
        case .german: "language.german"
        }
    }
}

@MainActor
private final class TranslationListModel: ObservableObject {
    @Published var translations: [TranslationSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let loader = TranslationsLoader()
    private var requestID = UUID()

    func load(language: String?) {
        let currentRequest = UUID()
        requestID = currentRequest
        isLoading = true
        errorMessage = nil

        loader.load(
            language: language,
            onSuccess: { [weak self] values in
                guard let self, self.requestID == currentRequest else { return }
                self.translations = values
                self.isLoading = false
            },
            onError: { [weak self] message in
                guard let self, self.requestID == currentRequest else { return }
                self.errorMessage = message
                self.isLoading = false
            }
        )
    }

    deinit {
        loader.close()
    }
}

struct ContentView: View {
    @StateObject private var model = TranslationListModel()
    @State private var filter = LanguageFilter.all

    private let navy = Color(red: 41 / 255, green: 74 / 255, blue: 101 / 255)
    private let primaryBlue = Color(red: 74 / 255, green: 107 / 255, blue: 138 / 255)
    private let cream = Color(red: 247 / 255, green: 245 / 255, blue: 241 / 255)
    private let gold = Color(red: 185 / 255, green: 154 / 255, blue: 90 / 255)

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                content
            }
            .background(cream.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
        .tint(primaryBlue)
        .task(id: filter) {
            model.load(language: filter.apiCode)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("app.name")
                .font(.system(size: 25, weight: .semibold, design: .serif))
                .foregroundStyle(navy)
            Text("app.subtitle")
                .font(.subheadline)
                .foregroundStyle(primaryBlue)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(Color.white.shadow(color: .black.opacity(0.08), radius: 4, y: 2))
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(gold)
                .frame(height: 2)
                .padding(.top, 22)

            Text("translations.title")
                .font(.system(size: 30, weight: .semibold, design: .serif))
                .foregroundStyle(navy)
                .padding(.top, 14)

            Text("translations.description")
                .font(.subheadline)
                .foregroundStyle(primaryBlue)
                .padding(.top, 4)

            Picker("language.label", selection: $filter) {
                ForEach(LanguageFilter.allCases) { option in
                    Text(option.title).tag(option)
                }
            }
            .pickerStyle(.segmented)
            .padding(.top, 16)
            .padding(.bottom, 14)

            stateContent
        }
        .padding(.horizontal, 20)
    }

    @ViewBuilder
    private var stateContent: some View {
        if model.isLoading {
            VStack(spacing: 14) {
                ProgressView()
                Text("translations.loading")
                    .foregroundStyle(primaryBlue)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.errorMessage != nil {
            VStack(spacing: 12) {
                Text("translations.error")
                    .font(.headline)
                    .foregroundStyle(navy)
                Button("action.retry") {
                    model.load(language: filter.apiCode)
                }
                .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.translations.isEmpty {
            Text("translations.empty")
                .foregroundStyle(primaryBlue)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.top, 24)
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(model.translations, id: \.code) { translation in
                        translationCard(translation)
                    }
                }
                .padding(.bottom, 28)
            }
        }
    }

    private func translationCard(_ translation: TranslationSummary) -> some View {
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(translation.name)
                    .font(.headline)
                    .foregroundStyle(navy)
                Text([translation.shortName, translation.code]
                    .compactMap { $0 }
                    .uniqued()
                    .joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(primaryBlue)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(translation.language.code.uppercased())
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color(red: 125 / 255, green: 161 / 255, blue: 194 / 255))
                if translation.isDefault {
                    Text("translations.default")
                        .font(.caption2)
                        .foregroundStyle(gold)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color(red: 237 / 255, green: 230 / 255, blue: 214 / 255), lineWidth: 1)
        )
    }
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
