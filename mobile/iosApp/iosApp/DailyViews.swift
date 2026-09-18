import SharedLogic
import SwiftUI

@MainActor
final class DailyContentModel: ObservableObject {
    @Published var prayers: [PrayerSummary] = []
    @Published var prayer: PrayerDetail?
    @Published var calendarDay: CalendarDay?
    @Published var isLoading = false
    @Published var failed = false

    private let loader = DailyContentLoader()
    private var requestID = UUID()

    func loadPrayers(language: String) {
        beginRequest { requestID in
            loader.loadPrayers(
                language: language,
                onSuccess: { [weak self] values in
                    guard let self, self.requestID == requestID else { return }
                    self.prayers = values
                    self.finish()
                },
                onError: { [weak self] _ in self?.fail(requestID) }
            )
        }
    }

    func loadPrayer(id: Int64) {
        beginRequest { requestID in
            loader.loadPrayer(
                id: id,
                onSuccess: { [weak self] value in
                    guard let self, self.requestID == requestID else { return }
                    self.prayer = value
                    self.finish()
                },
                onError: { [weak self] _ in self?.fail(requestID) }
            )
        }
    }

    func loadCalendarDay(date: String, language: String) {
        beginRequest { requestID in
            loader.loadCalendarDay(
                date: date,
                language: language,
                onSuccess: { [weak self] value in
                    guard let self, self.requestID == requestID else { return }
                    self.calendarDay = value
                    self.finish()
                },
                onError: { [weak self] _ in self?.fail(requestID) }
            )
        }
    }

    private func beginRequest(_ operation: (UUID) -> Void) {
        let id = UUID()
        requestID = id
        isLoading = true
        failed = false
        operation(id)
    }

    private func finish() {
        isLoading = false
        failed = false
    }

    private func fail(_ id: UUID) {
        guard requestID == id else { return }
        failed = true
        isLoading = false
    }

    deinit {
        loader.close()
    }
}

struct PrayersView: View {
    let language: String
    let onBack: () -> Void

    @StateObject private var model = DailyContentModel()
    @State private var selectedPrayerID: Int64?

    var body: some View {
        Group {
            if let selectedPrayerID {
                PrayerReaderView(
                    language: language,
                    prayerID: selectedPrayerID,
                    model: model,
                    onBack: {
                        self.selectedPrayerID = nil
                        model.prayer = nil
                    }
                )
            } else {
                VStack(spacing: 0) {
                    DailyHeader(title: localized("prayers.title", language: language), onBack: onBack)
                    if model.isLoading {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if model.failed {
                        DailyError(
                            language: language,
                            message: localized("prayers.error", language: language),
                            retry: { model.loadPrayers(language: language) }
                        )
                    } else if model.prayers.isEmpty {
                        Text(localized("prayers.empty", language: language))
                            .foregroundStyle(AppPalette.blue)
                            .multilineTextAlignment(.center)
                            .padding(24)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 10) {
                                ForEach(model.prayers, id: \.id) { prayer in
                                    Button(action: { selectedPrayerID = prayer.id }) {
                                        HStack(spacing: 12) {
                                            Image(systemName: "cross")
                                                .foregroundStyle(AppPalette.navy)
                                                .frame(width: 46, height: 46)
                                                .background(AppPalette.lightBlue, in: RoundedRectangle(cornerRadius: 13))
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(prayer.title)
                                                    .font(.headline)
                                                    .foregroundStyle(AppPalette.ink)
                                                Text(prayer.excerpt)
                                                    .font(.caption)
                                                    .foregroundStyle(AppPalette.blue)
                                                    .lineLimit(2)
                                            }
                                            Spacer()
                                            Text(prayer.languageCode.uppercased())
                                                .font(.caption.weight(.bold))
                                                .foregroundStyle(AppPalette.gold)
                                            Image(systemName: "chevron.right")
                                                .foregroundStyle(AppPalette.blue)
                                        }
                                        .padding(16)
                                        .background(Color.white, in: RoundedRectangle(cornerRadius: 18))
                                        .overlay { RoundedRectangle(cornerRadius: 18).stroke(AppPalette.border) }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(20)
                        }
                    }
                }
                .background(AppPalette.cream.ignoresSafeArea())
                .task { model.loadPrayers(language: language) }
            }
        }
    }
}

private struct PrayerReaderView: View {
    let language: String
    let prayerID: Int64
    @ObservedObject var model: DailyContentModel
    let onBack: () -> Void

    @AppStorage("prayerFontSize") private var fontSize = 19.0

    var body: some View {
        VStack(spacing: 0) {
            DailyHeader(
                title: model.prayer?.title ?? localized("prayers.title", language: language),
                onBack: onBack
            )
            if model.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if model.failed {
                DailyError(
                    language: language,
                    message: localized("prayer.error", language: language),
                    retry: { model.loadPrayer(id: prayerID) }
                )
            } else if let prayer = model.prayer {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if let intro = prayer.intro, !intro.isEmpty {
                            Text(intro)
                                .font(.system(.body, design: .serif))
                                .foregroundStyle(AppPalette.blue)
                        }
                        Text(prayer.body)
                            .font(.system(size: fontSize, design: .serif))
                            .foregroundStyle(AppPalette.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(22)
                }

                HStack {
                    Button("A−") { fontSize = max(fontSize - 1, 15) }.frame(maxWidth: .infinity)
                    Button("A+") { fontSize = min(fontSize + 1, 28) }.frame(maxWidth: .infinity)
                    ShareLink(item: "\(prayer.title)\n\n\(prayer.body)") {
                        Image(systemName: "square.and.arrow.up").frame(width: 50, height: 44)
                    }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 6)
                .background(Color.white.shadow(color: .black.opacity(0.12), radius: 5, y: -2))
            }
        }
        .background(AppPalette.cream.ignoresSafeArea())
        .task(id: prayerID) { model.loadPrayer(id: prayerID) }
    }
}

struct CalendarView: View {
    let language: String
    let onBack: () -> Void

    @StateObject private var model = DailyContentModel()
    @State private var date = Date()

    private var isoDate: String { Self.isoFormatter.string(from: date) }

    var body: some View {
        VStack(spacing: 0) {
            DailyHeader(title: localized("calendar.title", language: language), onBack: onBack)

            HStack {
                Button(action: { moveDay(-1) }) {
                    Image(systemName: "chevron.left").frame(width: 44, height: 44)
                }
                VStack(spacing: 3) {
                    Text(Self.displayFormatter(language).string(from: date))
                        .font(.system(.title2, design: .serif).weight(.bold))
                        .foregroundStyle(AppPalette.ink)
                    if let day = model.calendarDay,
                       let oldDate = Self.isoFormatter.date(from: day.oldStyleDate) {
                        Text(localized("calendar.old", language: language, Self.displayFormatter(language).string(from: oldDate)))
                            .font(.caption)
                            .foregroundStyle(AppPalette.blue)
                    }
                }
                .frame(maxWidth: .infinity)
                Button(action: { moveDay(1) }) {
                    Image(systemName: "chevron.right").frame(width: 44, height: 44)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 12)

            if model.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if model.failed {
                DailyError(
                    language: language,
                    message: localized("calendar.error", language: language),
                    retry: load
                )
            } else if let day = model.calendarDay {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        Text(day.liturgicalPeriod)
                            .font(.system(.title2, design: .serif).weight(.bold))
                            .foregroundStyle(AppPalette.navy)

                        if !day.fastingEvents.isEmpty {
                            CalendarGroupView(
                                title: localized("calendar.fasting", language: language),
                                values: day.fastingEvents.map(\.name),
                                highlighted: true
                            )
                        }
                        if !day.events.isEmpty {
                            CalendarGroupView(
                                title: localized("calendar.events", language: language),
                                values: day.events.map(\.name)
                            )
                        }
                        if !day.readings.isEmpty {
                            CalendarGroupView(
                                title: localized("calendar.readings", language: language),
                                values: day.readings.map { $0.displayRef.isEmpty ? $0.title : $0.displayRef }
                            )
                        }
                        if day.fastingEvents.isEmpty && day.events.isEmpty && day.readings.isEmpty {
                            Text(localized("calendar.empty", language: language))
                                .foregroundStyle(AppPalette.blue)
                        }
                    }
                    .padding(20)
                }
            }
        }
        .background(AppPalette.cream.ignoresSafeArea())
        .task(id: isoDate) { load() }
    }

    private func moveDay(_ offset: Int) {
        date = Calendar.current.date(byAdding: .day, value: offset, to: date) ?? date
    }

    private func load() {
        model.loadCalendarDay(date: isoDate, language: language)
    }

    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static func displayFormatter(_ language: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.locale = Locale(identifier: language)
        return formatter
    }
}

private struct CalendarGroupView: View {
    let title: String
    let values: [String]
    var highlighted = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline).foregroundStyle(AppPalette.navy)
            ForEach(Array(values.enumerated()), id: \.offset) { index, value in
                if index > 0 { Divider() }
                Text(value).foregroundStyle(AppPalette.ink)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(highlighted ? AppPalette.lightBlue : Color.white, in: RoundedRectangle(cornerRadius: 18))
        .overlay { RoundedRectangle(cornerRadius: 18).stroke(AppPalette.border) }
    }
}

private struct DailyHeader: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").frame(width: 44, height: 44)
            }
            Text(title)
                .font(.system(.title3, design: .serif).weight(.bold))
                .foregroundStyle(AppPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color.white)
    }
}

private struct DailyError: View {
    let language: String
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(message).foregroundStyle(AppPalette.ink).multilineTextAlignment(.center)
            Button(localized("action.retry", language: language), action: retry)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
}
