// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';

import 'package:bird_alarm/main.dart';

void main() {
  testWidgets('Bird alarm home renders core controls', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const BirdAlarmApp());
    await tester.pumpAndSettle();

    expect(find.text('鸟瘾闹钟'), findsWidgets);
    expect(find.text('下一次唤醒'), findsOneWidget);
    expect(find.text('鸟鸣'), findsWidgets);
    expect(find.text('新闹钟'), findsOneWidget);
  });
}
