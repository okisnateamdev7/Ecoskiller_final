import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class SchoolSharkTankPage extends StatefulWidget {
  final Map<String, dynamic> user;
  const SchoolSharkTankPage({super.key, required this.user});

  @override
  State<SchoolSharkTankPage> createState() => _SchoolSharkTankPageState();
}

class _SchoolSharkTankPageState extends State<SchoolSharkTankPage> {
  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      body: CustomScrollView(
        slivers: [
          _buildAppBar(context),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSetupCard(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('INSTITUTIONAL ROUNDS'),
                  const SizedBox(height: 16),
                  _buildRoundList(),
                  const SizedBox(height: 32),
                  _buildSectionHeader('STUDENT PARTICIPATION'),
                  const SizedBox(height: 16),
                  _buildParticipationStats(),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return SliverAppBar(
      expandedHeight: 120,
      pinned: true,
      title: Text(
        'CAMPUS SHARK TANK',
        style: GoogleFonts.inter(fontWeight: FontWeight.w900, letterSpacing: 2, fontSize: 16),
      ),
      actions: [
        IconButton(icon: const Icon(LucideIcons.plus), onPressed: () {}),
      ],
    );
  }

  Widget _buildSetupCard() {
    return FadeInUp(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [Color(0xFF6a11cb), Color(0xFF2575fc)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(LucideIcons.flag, color: Colors.white, size: 32),
            const SizedBox(height: 16),
            Text(
              'START A CAMPAIGN',
              style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 18),
            ),
            const SizedBox(height: 8),
            Text(
              'Organize a shark tank event for your institution. Invite judges and mentors.',
              style: GoogleFonts.inter(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () {},
              style: ElevatedButton.styleFrom(backgroundColor: Colors.white, foregroundColor: Colors.blue),
              child: const Text('CREATE ROUND'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildRoundList() {
    return Column(
      children: [
        _buildRoundItem('Inter-School Innovation 2024', 'Live in 2 Days', 45),
        const SizedBox(height: 12),
        _buildRoundItem('Junior Tech Pitch', 'Finished', 120),
      ],
    );
  }

  Widget _buildRoundItem(String title, String status, int students) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.grey.withOpacity(0.1)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
                Text(status, style: GoogleFonts.inter(fontSize: 12, color: AppTheme.linkedinBlue)),
              ],
            ),
          ),
          Column(
            children: [
              Text(students.toString(), style: GoogleFonts.inter(fontWeight: FontWeight.w900)),
              Text('STUDENTS', style: GoogleFonts.inter(fontSize: 8, color: Colors.grey)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildParticipationStats() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
      ),
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _StatCol('SUBMITTED', '165'),
          _StatCol('PENDING', '24'),
          _StatCol('WINNERS', '3'),
        ],
      ),
    );
  }
}

class _StatCol extends StatelessWidget {
  final String label;
  final String value;
  const _StatCol(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value, style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 20)),
        Text(label, style: GoogleFonts.inter(fontSize: 9, color: Colors.grey, letterSpacing: 1)),
      ],
    );
  }
}
