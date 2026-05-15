import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class TrainerSharkTankPage extends StatelessWidget {
  final Map<String, dynamic> user;
  const TrainerSharkTankPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      appBar: AppBar(
        title: Text('PITCH TRAINING NODE', style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 16, letterSpacing: 2)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildTrainingHeader(),
            const SizedBox(height: 32),
            _buildSectionHeader('MY TRAINEES'),
            const SizedBox(height: 16),
            _buildTraineeList(),
            const SizedBox(height: 32),
            _buildSectionHeader('PREP RESOURCES'),
            const SizedBox(height: 16),
            _buildResourceGrid(),
          ],
        ),
      ),
    );
  }

  Widget _buildTrainingHeader() {
    return FadeInDown(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFFff9966), Color(0xFFff5e62)]),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(LucideIcons.mic, color: Colors.white, size: 32),
            const SizedBox(height: 16),
            Text(
              'PITCH COACHING',
              style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 18),
            ),
            const SizedBox(height: 8),
            Text(
              'Helping candidates master their presentation skills for the big stage.',
              style: GoogleFonts.inter(color: Colors.white70, fontSize: 13),
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

  Widget _buildTraineeList() {
    return Column(
      children: [
        _TraineeItem('Rahul Verma', 'Pitch Prep: 80%'),
        const SizedBox(height: 12),
        _TraineeItem('Anita Das', 'Pitch Prep: 45%'),
      ],
    );
  }

  Widget _buildResourceGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.5,
      children: [
        _ResourceCard('Deck Templates', LucideIcons.layout),
        _ResourceCard('Body Language', LucideIcons.smile),
      ],
    );
  }
}

class _TraineeItem extends StatelessWidget {
  final String name;
  final String progress;
  const _TraineeItem(this.name, this.progress);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const CircleAvatar(radius: 14, backgroundColor: Colors.orange),
          const SizedBox(width: 16),
          Expanded(child: Text(name, style: GoogleFonts.inter(fontWeight: FontWeight.bold))),
          Text(progress, style: GoogleFonts.inter(fontSize: 12, color: Colors.orange)),
        ],
      ),
    );
  }
}

class _ResourceCard extends StatelessWidget {
  final String label;
  final IconData icon;
  const _ResourceCard(this.label, this.icon);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, color: AppTheme.linkedinBlue),
          const SizedBox(height: 8),
          Text(label, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.bold), textAlign: TextAlign.center),
        ],
      ),
    );
  }
}
