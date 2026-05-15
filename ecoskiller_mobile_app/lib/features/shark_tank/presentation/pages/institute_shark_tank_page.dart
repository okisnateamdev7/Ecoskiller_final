import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class InstituteSharkTankPage extends StatelessWidget {
  final Map<String, dynamic> user;
  const InstituteSharkTankPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      appBar: AppBar(
        title: Text('INSTITUTE INNOVATION NODE', style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 14, letterSpacing: 1.5)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildInstituteHeader(),
            const SizedBox(height: 32),
            _buildSectionHeader('AFFILIATED SCHOOLS'),
            const SizedBox(height: 16),
            _buildSchoolGrid(),
            const SizedBox(height: 32),
            _buildSectionHeader('GLOBAL PARTNERSHIPS'),
            const SizedBox(height: 16),
            _buildPartnershipList(),
          ],
        ),
      ),
    );
  }

  Widget _buildInstituteHeader() {
    return FadeInUp(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFF8e2de2), Color(0xFF4a00e0)]),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(LucideIcons.university, color: Colors.white, size: 32),
            const SizedBox(height: 16),
            Text(
              'INNOVATION NETWORK',
              style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 18),
            ),
            const SizedBox(height: 8),
            Text(
              'Managing 12 schools and 4500+ student innovators across the network.',
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

  Widget _buildSchoolGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.3,
      children: [
        _MetricCard('Active Ideas', '124', Colors.purpleAccent),
        _MetricCard('Total Funding', '₹12M', Colors.blueAccent),
      ],
    );
  }

  Widget _buildPartnershipList() {
    return Column(
      children: [
        _PartnerRow('Stanford Tech Hub', 'Resource Sharing'),
        const SizedBox(height: 12),
        _PartnerRow('MIT Innovation Lab', 'Expert Mentorship'),
      ],
    );
  }
}

class _MetricCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _MetricCard(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(value, style: GoogleFonts.inter(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
          Text(label, style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}

class _PartnerRow extends StatelessWidget {
  final String name;
  final String type;
  const _PartnerRow(this.name, this.type);

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
          const Icon(LucideIcons.globe, color: AppTheme.linkedinBlue, size: 20),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text(type, style: GoogleFonts.inter(fontSize: 12, color: Colors.grey)),
            ],
          ),
        ],
      ),
    );
  }
}
